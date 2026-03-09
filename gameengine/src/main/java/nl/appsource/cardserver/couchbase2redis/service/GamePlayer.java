package nl.appsource.cardserver.couchbase2redis.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converters.service.GameToOpenApiConverter;
import nl.appsource.cardserver.couchbase.repository.BoomRepository;
import nl.appsource.cardserver.couchbase.repository.GameRepository;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.couchbase.utils.AiPlayer;
import nl.appsource.cardserver.couchbase.utils.GameEngineImpl;
import nl.appsource.cardserver.couchbase2redis.GameEngineRw;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import nl.appsource.cardserver.openapi.service.RedisPubSubService;
import nl.appsource.generated.openapi.model.GameEvent;
import nl.appsource.generated.openapi.model.MessageEvent;
import nl.appsource.generated.openapi.model.UserMessage;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import tools.jackson.databind.json.JsonMapper;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.max;
import static java.lang.Runtime.getRuntime;
import static nl.appsource.cardserver.converters.service.GameToOpenApiConverter.convertCard;
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
    private final JsonMapper jsonMapper;

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
                        .map(GameEngineRw::new)
                        .subscribe(this::scheduleNext);
                });
        }

        scheduler.scheduleWithFixedDelay(this::processDueEvents, 5000, 500, TimeUnit.MILLISECONDS);

        redisPubSubService.listenTo("gameEvent").subscribe(myServerSentEvent -> {
            if (myServerSentEvent.event().equals("gameEvent")) {
                log.info("gameUpdate to gameId={}", myServerSentEvent.data());
                final GameEvent gameEvent = jsonMapper.convertValue(myServerSentEvent.data(), GameEvent.class);
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
        Mono<GameEngineRw> run();
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

    public Mono<GameEngineRw> catchException(final GameEngineExecutor gameEngineExecutor) {
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
                        .filter(game -> gameEvent.getUserId() == null || isAiPlayer(gameEvent.getUserId()) || game.getCreator().equals(gameEvent.getUserId()) || game.getPlayers().contains(gameEvent.getUserId()))
                        //                .doOnNext(game -> {
                        //                    log.info("Executing event: {} for game {} userId: {}", gameEventType, gameId, userId);
                        //                })
                        .map(GameEngineRw::new)
                        .filter(gameEngine -> !gameEngine.gameEngine().isCompleted())
                        .flatMap(gameEngineRw -> {

                            final String userId = gameEvent.getUserId();
                            final String gameId = gameEvent.getGameId();
                            final Optional<Boolean> say = gameEvent.getSay();
                            final Optional<Card> card = gameEvent.getCard().map(GameToOpenApiConverter::convertCard);

                            final Mono<GameEngineRw> result = switch (gameEvent.getEventType()) {
                                case OPEN_LAST_TRICK -> catchException(gameEngineRw::openLastTrick);
                                case CLOSE_LAST_TRICK -> catchException(gameEngineRw::closeLastTrick);
                                case PLAY_CARD -> catchException(() -> gameEngineRw.playCard(userId, card.orElseThrow()));
                                case SAY -> catchException(() -> gameEngineRw.say(userId, say.orElseThrow()));
                                case CHECK_ROTATE -> catchException(gameEngineRw::checkNiemandIsGegaanEnIedereenHeeftGezegd);
                                case CLAIM_ROEM -> catchException(() -> gameEngineRw.claimRoem(userId));
                                case CLAIM_VERZAKEN -> catchException(() -> claimVerzaken(userId, gameId));
                            };

                            return result
                                .map(GameEngineRw::game)
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
                                .doOnNext(game -> log.info("executeSynchronious() executed gameEventType:" + gameEvent.getEventType() + ", userId=" + gameEvent.getUserId() + ", gameId=" + gameEvent.getGameId() + ", card=" + gameEvent.getCard()))
                                .flatMap(game -> {
                                    if (game.getBoomId() != null) {
                                        return boomRepository.findById(game.getBoomId())
                                            .map(boomRepository::save)
                                            .then(Mono.just(game));
                                    } else {
                                        return Mono.just(game);
                                    }
                                })

                                .doFinally((_unused) -> this.scheduleNext(gameEngineRw));
                        });
                }
            )
            .then()
            .onErrorResume(throwable -> {

                log.error("executeSynchronious()", throwable);

                if (gameEvent.getUserId() != null && !isAiPlayer(gameEvent.getUserId())) {
                    final String message = throwable.getClass().getName() + ":" + throwable.getMessage();
                    return redisPubSubService.publish(gameEvent.getUserId(), MyServerSentEvent.messageEvent(MessageEvent.builder().message(UserMessage.builder().userId(gameEvent.getUserId()).message(message).variant(UserMessage.VariantEnum.ERROR).build()).build())).then();
                } else {
                    return Mono.empty();
                }

            })
            .subscribe();
    }

    private void scheduleNext(final GameEngineRw gameEngine) {

        if (gameEngine.gameEngine().isCompleted()) {
            return;
        }

        if (gameEngine.gameEngine().getGame()
            .getLastTrickOpen()) {
            return;
        }


        if (gameEngine.gameEngine().isAiSay()) {
            final String userId = gameEngine.game().getPlayers().get(gameEngine.gameEngine().calcWhoSay());

            if (!isAiPlayer(userId)) {
                throw new IllegalStateException("Not AI player");
            }

            scheduleGameEvent(GameEvent.builder().gameId(gameEngine.gameEngine().getGame().getId()).eventType(GameEvent.EventTypeEnum.SAY).say(Optional.of(new AiPlayer(gameEngine.gameEngine()).decideBid(userId))).executionTime(System.currentTimeMillis() + 2000 + RAND.nextLong(1000)).build());
        } else if (gameEngine.gameEngine().isAiTurn()) {

            final String userId = gameEngine.game().getPlayers().get(gameEngine.gameEngine().calcWhoHasTurn());

            if (!isAiPlayer(userId)) {
                throw new IllegalStateException("Not AI player");
            }

            scheduleGameEvent(GameEvent.builder().gameId(gameEngine.gameEngine().getGame().getId()).eventType(GameEvent.EventTypeEnum.PLAY_CARD).card(Optional.of(convertCard(new AiPlayer(gameEngine.gameEngine()).calcAiCard(userId)))).executionTime(System.currentTimeMillis() + (gameEngine.gameEngine().isFullTrick() ? 4000 : 2000) + RAND.nextLong(500)).build());
        }

    }


    public void scheduleGameEvent(final GameEvent gameEvent) {

        if (gameEvent.getUserId() == null) {
            log.error("userId = null , not scheduling ", new RuntimeException("not scheduling exmpty userId"));
        }

        eventQueue.add(gameEvent);
    }

    //    @Override
    //    @Override
    public Mono<GameEngineRw> claimVerzaken(final String userId, final String gameId) {
        return gameRepository.findById(gameId)
            .map(GameEngineImpl::new)
            .flatMap(gameEngine -> {
                final int slagNr = gameEngine.calcTricksPlayed();

                final int laatsteCompleteSlag = slagNr - (slagNr > 0 && gameEngine.getTurnCount() % 4 == 0 ? 1 : 0);

                return Flux.just(0, 1, 2, 3)
                    .filter(playerNr -> gameEngine.verzaakt(laatsteCompleteSlag, playerNr))
                    .collectList()
                    .flatMap(verzaakteSpelers -> {
                        if (verzaakteSpelers.isEmpty()) {
                            return redisPubSubService.publish(userId, MyServerSentEvent.messageEvent(MessageEvent.builder().message(UserMessage.builder().userId(userId).message("Er is niet verzaakt in slag " + laatsteCompleteSlag).variant(UserMessage.VariantEnum.INFO).build()).build()));
                        } else {
                            return Flux.fromIterable(verzaakteSpelers)
                                .flatMap(playerNr -> userRepository.findById(gameEngine.getGame().getPlayers().get(playerNr))
                                    .flatMap(player -> redisPubSubService.publish(userId, MyServerSentEvent.messageEvent(MessageEvent.builder().message(UserMessage.builder().userId(userId).message("Er is verzaakt in slag " + laatsteCompleteSlag + " door " + player.getDisplayName()).variant(UserMessage.VariantEnum.ERROR).build()).build())))
                                ).then();
                        }
                    });
            })
            .then(Mono.empty());
    }


}
