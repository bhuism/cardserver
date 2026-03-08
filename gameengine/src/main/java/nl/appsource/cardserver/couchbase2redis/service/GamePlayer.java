package nl.appsource.cardserver.couchbase2redis.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.couchbase.repository.BoomRepository;
import nl.appsource.cardserver.couchbase.repository.GameRepository;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.couchbase.utils.GameEngine;
import nl.appsource.cardserver.couchbase.utils.GameEngineImpl;
import nl.appsource.cardserver.couchbase2redis.GameEngineRw;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.openapi.service.RedisPubSubService;
import nl.appsource.generated.openapi.model.GameEvent;
import nl.appsource.generated.openapi.model.UserMessage;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.max;
import static java.lang.Runtime.getRuntime;
import static nl.appsource.cardserver.couchbase.utils.GameEngineImpl.isAiPlayer;

@RequiredArgsConstructor
@Slf4j
@Service
@Profile("!citest")
public class GamePlayer {

    private final RedisPubSubService redisPubSubService;

    private final GameRepository gameRepository;

    private final Environment environment;

    private static final Random RAND = new SecureRandom();

    private final PriorityQueue<GameEvent> eventQueue = new PriorityQueue<>(Comparator.comparingLong(GameEvent::getExecutionTime));

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(max(1, getRuntime().availableProcessors() - 1));

    private final UserRepository userRepository;

    private final BoomRepository boomRepository;

    boolean stop = false;

    @PostConstruct
    public void init() {
        log.info("init()");
        if (environment.acceptsProfiles(Profiles.of("production", "development"))) {
            gameRepository.findAll()
                .filter((game) -> game.getTurns().size() != 32)
                .map(Game::getId)
                .subscribe(gameId -> {
                    executeSynchronious(GameEvent.builder().eventType(GameEvent.EventTypeEnum.CLOSE_LAST_TRICK).gameId(gameId).build());
                    executeSynchronious(GameEvent.builder().eventType(GameEvent.EventTypeEnum.CHECK_ROTATE).gameId(gameId).build());
                    gameRepository.findById(gameId)
                        .map(GameEngineImpl::new)
                        .subscribe(this::scheduleNext);
                });
        }

        scheduler.scheduleWithFixedDelay(this::processDueEvents, 5000, 500, TimeUnit.MILLISECONDS);

        redisPubSubService.listenTo("gameEvent").subscribe(myServerSentEvent -> {
            if (myServerSentEvent.event().equals("gameEvent")) {
                log.info("gameUpdate to gameId={}", myServerSentEvent.data());
                final GameEvent gameEvent = (GameEvent) myServerSentEvent.data();
                executeSynchronious(gameEvent);
            }
        });

    }

    @PreDestroy
    public void destroy() {
        stop = true;
        scheduler.shutdown();
    }

    @FunctionalInterface
    public interface GameEngineExecutor {
        Mono<GameEngine> run();
    }

    private void processDueEvents() {
        long currentTime = System.currentTimeMillis();
        while (!eventQueue.isEmpty() && eventQueue.peek().getExecutionTime() <= currentTime) {
            final GameEvent eventToExecute = eventQueue.poll();
            if (eventToExecute != null) {
                try {
                    executeSynchronious(eventToExecute);
                } catch (Throwable t) {
                    log.error("Dont exception in a worker thread", t);
                }
            }
        }
    }

    public Mono<GameEngine> catchException(final GameEngineExecutor gameEngineExecutor) {
        return gameEngineExecutor.run();
    }

    public void executeSynchronious(final GameEvent gameEvent) {

        if (gameEvent.getUserId() == null && gameEvent.getEventType() != GameEvent.EventTypeEnum.CHECK_ROTATE && gameEvent.getEventType() != GameEvent.EventTypeEnum.CLOSE_LAST_TRICK) {
            log.error("userId === null, gameEventType=" + gameEvent.getEventType());
        }

        eventQueue.removeIf(scheduledGameEvent -> scheduledGameEvent.getGameId().equals(gameEvent.getGameId()));

        Mono.just(gameEvent.getGameId())
            .flatMap(id -> gameRepository.lock(id, Duration.ofMillis(500), Game.class))
            .retryWhen(Retry.backoff(5, Duration.ofMillis(100)).doAfterRetry(retrySignal -> {
                log.info("Retrying lock because of: " + retrySignal.toString());
            }))
            .flatMap(entry -> {
                    return Mono.just(entry.getKey())
                        .filter(game -> userId == null || isAiPlayer(userId) || game.getCreator().equals(userId) || game.getPlayers().contains(userId))
                        //                .doOnNext(game -> {
                        //                    log.info("Executing event: {} for game {} userId: {}", gameEventType, gameId, userId);
                        //                })
                        .map(GameEngineRw::new)
                        .filter(gameEngine -> !gameEngine.gameEngine().isCompleted())
                        .flatMap(gameEngineRw -> {

                            final Mono<GameEngine> result = switch (gameEvent.getEventType()) {
                                case AI_SAY -> catchException(gameEngineRw::sayAi);
                                case AI_PLAY_CARD -> catchException(gameEngineRw::playAiCard);
                                case OPEN_LAST_TRICK -> catchException(gameEngineRw::openLastTrick);
                                case CLOSE_LAST_TRICK -> catchException(gameEngineRw::closeLastTrick);
                                case HUMAN_PLAY_CARD -> catchException(() -> gameEngineRw.playCard(userId, card));
                                case HUMAN_SAY -> catchException(() -> gameEngineRw.say(userId, say));
                                case CHECK_ROTATE -> catchException(gameEngineRw::checkNiemandIsGegaanEnIedereenHeeftGezegd);
                                case CLAIM_ROEM -> catchException(() -> gameEngineRw.claimRoem(userId));
                            };

                            return result
                                .map(GameEngine::getGame)
                                .flatMap(game -> gameRepository.updateLocked(game.getId(), game, entry.getValue()).then(Mono.just(game)))
                                .onErrorResume(error -> {
                                    log.error("Error during update, attempting to unlock game: {}", gameId);
                                    return gameRepository.unLockNoSave(gameId, entry.getValue())
                                        // Swallow unlock-specific errors so we don't mask the original error
                                        .onErrorResume(unlockError -> {
                                            log.warn("Failed to cleanly unlock document: {}", gameId);
                                            return Mono.empty();
                                        })
                                        // Re-throw the original error to the subscriber
                                        .then(Mono.error(error));
                                })
                                .doOnNext(game -> log.info("executeSynchronious() executed gameEventType:" + gameEventType.name() + ", userId=" + userId + ", gameId=" + gameId + ", card=" + card + ", say=" + say))
                                .flatMap(game -> {
                                    if (game.getBoomId() != null) {
                                        return boomRepository.findById(game.getBoomId())
                                            .map(boomRepository::save)
                                            .then(Mono.just(game));
                                    } else {
                                        return Mono.just(game);
                                    }
                                })

                                .doFinally((_unused) -> this.scheduleNext(gameEngine));
                        });
                }
            )

            .doOnError(throwable -> {
                log.error("executeSynchronious()", throwable);
                if (userId != null && !isAiPlayer(userId)) {
                    sseEventSender.sendUserIdMessage(userId, userId, throwable.getClass().getName() + ":" + throwable.getMessage(), UserMessage.VariantEnum.ERROR).subscribe();
                }
            })
            .subscribe();
    }

    private void scheduleNext(final GameEngine gameEngine) {

        if (gameEngine.isCompleted()) {
            return;
        }

        if (gameEngine.getGame()
            .getLastTrickOpen()) {
            return;
        }

        if (gameEngine.isAiSay()) {
            scheduleGameEvent(new ScheduledGameEvent(System.currentTimeMillis() + 2000 + RAND.nextInt(1000), gameEngine.getGame()
                .getPlayers()
                .get(gameEngine.calcWhoSay()), GameEventType.AI_SAY, gameEngine.getGame()
                .getId()));
        } else if (gameEngine.isAiTurn()) {
            scheduleGameEvent(new ScheduledGameEvent(System.currentTimeMillis() + (gameEngine.isFullTrick() ? 4000 : 2000) + RAND.nextInt(500), gameEngine.getGame()
                .getPlayers()
                .get(gameEngine.calcWhoHasTurn()), GameEventType.AI_PLAY_CARD, gameEngine.getGame()
                .getId()));
        }
    }


    @Override
    public void scheduleGameEvent(final ScheduledGameEvent scheduledGameEvent) {

        if (scheduledGameEvent.getUserId() == null) {
            log.error("userId = null , not scheduling ", new RuntimeException("not scheduling exmpty userId"));
        }

        eventQueue.add(scheduledGameEvent);
    }



}
