package nl.appsource.cardserver.gameengine.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converters.service.BoomToOpenApiConverter;
import nl.appsource.cardserver.converters.service.GameToOpenApiConverter;
import nl.appsource.cardserver.couchbase.repository.BoomRepository;
import nl.appsource.cardserver.couchbase.repository.GameRepository;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.couchbase.utils.GameEngineImpl;
import nl.appsource.cardserver.gameengine.GameEngineRw;
import nl.appsource.cardserver.gameengine.GameEngineRwImpl;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.openapi.config.KafkaTopics;
import nl.appsource.cardserver.openapi.service.KafkaSender;
import nl.appsource.cardserver.openapi.service.SseEventSender;
import nl.appsource.generated.openapi.model.GameEvent;
import nl.appsource.generated.openapi.model.MessageEvent;
import nl.appsource.generated.openapi.model.UserMessage;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.max;
import static java.lang.Runtime.getRuntime;
import static nl.appsource.cardserver.openapi.MyServerSentEvent.messageEvent;
import static nl.appsource.cardserver.utils.Utils.isAiPlayer;

@RequiredArgsConstructor
@Slf4j
@Service
@Profile("!citest")
public class WorkerImpl implements Worker {

    private final GameRepository gameRepository;

    private final Environment environment;

    private final PriorityQueue<GameEvent> eventQueue = new PriorityQueue<>(Comparator.comparingLong(GameEvent::getExecutionTime));

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(max(1, getRuntime().availableProcessors() - 1));

    private final UserRepository userRepository;

    private final BoomRepository boomRepository;

    private final GameToOpenApiConverter gameToOpenApiConverter;

    private final BoomToOpenApiConverter boomToOpenApiConverter;

    private final KafkaSender kafkaSender;

    private final SseEventSender sseEventSender;

    private final JsonMapper jsonMapper;

    boolean stop = false;

    private Disposable streamSubscription;

    @PostConstruct
    public void init() {
        log.info("init()");

        final GameEngineRwImpl.UserMessenger noOpuserMessenger = new GameEngineRwImpl.UserMessenger() {

            @Override
            public Mono<Void> sendUserMessage(final String message) {
                return Mono.empty();
            }

            @Override
            public Mono<Void> sendGameMessage(final String message) {
                return Mono.empty();
            }
        };

        if (environment.acceptsProfiles(Profiles.of("production", "development"))) {
            gameRepository.findAll()
                .filter((game) -> game.getTurns().size() != 32)
                .map(Game::getId)
                .flatMap(gameRepository::findById)
                .map(game -> new GameEngineRwImpl(null, game, noOpuserMessenger))
                .flatMap(GameEngineRwImpl::rotateTrump)
                .flatMap(gameRepository::save)
                .flatMap((game) -> sseEventSender.updateGame(gameToOpenApiConverter.convert(game)))
                .doOnNext((game) -> kafkaSender.send(KafkaTopics.GAME_CHANGES, jsonMapper.writeValueAsString(game)))
                .subscribe();
        }

        scheduler.scheduleWithFixedDelay(this::processDueEvents, 5000, 500, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void destroy() {
        stop = true;
        scheduler.shutdown();

        if (this.streamSubscription != null && !this.streamSubscription.isDisposed()) {
            log.info("Disposing stream subscription...");
            this.streamSubscription.dispose();
            this.streamSubscription = null;
        }
    }

    private void processDueEvents() {
        long currentTime = System.currentTimeMillis();
        while (!eventQueue.isEmpty() && eventQueue.peek().getExecutionTime() <= currentTime) {
            final GameEvent eventToExecute = eventQueue.poll();
            if (eventToExecute != null) {
                try {
                    eventQueue.removeIf(scheduledGameEvent -> scheduledGameEvent.getGameId().equals(eventToExecute.getGameId()));
                    executeSynchronious(eventToExecute).subscribe();
                } catch (Throwable t) {
                    log.error("Dont exception in a worker thread", t);
                }
            }
        }
    }

    public Mono<Void> executeSynchronious(final GameEvent gameEvent) {

        return Mono.just(gameEvent.getGameId())
            .flatMap(id -> gameRepository.lock(id, Duration.ofMillis(500), Game.class))
            .retryWhen(Retry.backoff(5, Duration.ofMillis(100)).doAfterRetry(retrySignal -> {
                log.info("Retrying lock because of: " + retrySignal.toString());
            }))
            .flatMap(entry -> Mono.just(entry.getKey())
                    .filter(game -> gameEvent.getUserId() == null || isAiPlayer(gameEvent.getUserId()) || game.getCreator().equals(gameEvent.getUserId()) || game.getPlayers().contains(gameEvent.getUserId()))
                    .map(game -> {

                        final GameEngineRwImpl.UserMessenger userMessenger = new GameEngineRwImpl.UserMessenger() {

                            @Override
                            public Mono<Void> sendUserMessage(final String message) {
                                kafkaSender.sendSse(messageEvent(new MessageEvent().message(new UserMessage().userId(gameEvent.getUserId()).message(message).variant(UserMessage.VariantEnum.INFO)), Set.of(gameEvent.getUserId())));
                                return Mono.empty();
                            }

                            @Override
                            public Mono<Void> sendGameMessage(final String message) {
                                kafkaSender.sendSse(messageEvent(new MessageEvent().message(new UserMessage().userId(gameEvent.getUserId()).message(message).variant(UserMessage.VariantEnum.INFO)), new HashSet(game.getPlayers())));
                                return Mono.empty();
                            }
                        };

                        return new GameEngineRwImpl(gameEvent.getUserId(), game, userMessenger);
                    })
                    .filter(gameEngine -> !gameEngine.gameEngine().isCompleted())
                    .map(gameEngineRw -> (GameEngineRw) gameEngineRw)
                    .flatMap(gameEngineRw -> {
                        final String userId = gameEvent.getUserId();
                        return switch (gameEvent.getEventType()) {
                            case OPEN_LAST_TRICK -> gameEngineRw.openLastTrick();
                            case CLOSE_LAST_TRICK -> gameEngineRw.closeLastTrick();
                            case PLAY_CARD -> gameEngineRw.playCard(GameToOpenApiConverter.convertCard(Optional.ofNullable(gameEvent.getCard()).orElseThrow()));
                            case SAY -> gameEngineRw.say(gameEvent.getSay());
                            case CLAIM_ROEM -> gameEngineRw.claimRoem();
                            case CLAIM_VERZAKEN -> claimVerzaken(userId, entry.getKey().getId());
                        };
                    })
                    .flatMap(game -> gameRepository.updateLocked(game.getId(), game, entry.getValue()).then(Mono.just(game)))
                    .delayUntil(game -> sseEventSender.updateGame(gameToOpenApiConverter.convert(game)))
                    .doOnNext((game) -> kafkaSender.send(KafkaTopics.GAME_CHANGES, jsonMapper.writeValueAsString(game)))
                    .flatMap(game -> {
                        if (game.getBoomId() != null) {
                            return boomRepository.findById(game.getBoomId())
                                .flatMap(boomRepository::save)
                                .flatMap((boom) -> sseEventSender.updateBoom(boomToOpenApiConverter.convert(boom)))
                                .then(Mono.just(game));
                        } else {
                            return Mono.just(game);
                        }
                    }).onErrorResume(error -> {
                        log.error("Error during update, attempting to unlock game: {}", entry.getKey().getId());
                        return gameRepository.unLockNoSave(entry.getKey().getId(), entry.getValue())
                            // Swallow unlock-specific errors so we don't mask the original error
                            .onErrorResume(unlockError -> {
                                log.warn("Failed to cleanly unlock document: {}", entry.getKey().getId());
                                return Mono.empty();
                            })
                            // Re-throw the original error to the subscriber
                            .then(Mono.error(error));
                    })
                    .doFinally(signalType -> {
                        gameRepository.unLockNoSave(entry.getKey().getId(), entry.getValue()).onErrorResume((e) -> Mono.empty()).subscribe();
                    })
            )
            .onErrorResume(throwable -> {

                log.error("executeSynchronious()", throwable);

                if (gameEvent.getUserId() != null) {
                    final String message = throwable.getClass().getName() + ":" + throwable.getMessage();
                    kafkaSender.sendSse(messageEvent(new MessageEvent().message(new UserMessage().userId(gameEvent.getUserId()).message(message).variant(UserMessage.VariantEnum.ERROR)), Set.of(gameEvent.getUserId())));
                }
                return Mono.empty();
            })
            .then();
    }

    @Override
    public void scheduleGameEvent(final GameEvent gameEvent) {

        if (gameEvent.getUserId() == null) {
            log.error("userId = null , not scheduling ", new RuntimeException("not scheduling empty userId"));
        }

        if (gameEvent.getGameId() == null) {
            log.error("gameId = null , not scheduling ", new RuntimeException("not scheduling empty gameId"));
        }

        if (gameEvent.getEventType() == null) {
            log.error("eventType = null , not scheduling ", new RuntimeException("not scheduling empty eventType"));
        }

        eventQueue.add(gameEvent);
    }

    //    @Override
    //    @Override
    public Mono<Game> claimVerzaken(final String userId, final String gameId) {
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
                            kafkaSender.sendSse(messageEvent(new MessageEvent().message(new UserMessage().userId(userId).message("Er is niet verzaakt in slag " + laatsteCompleteSlag).variant(UserMessage.VariantEnum.INFO)), Set.of(userId)));
                            return Mono.empty();
                        } else {
                            return Flux.fromIterable(verzaakteSpelers)
                                .flatMap(playerNr -> userRepository.findById(gameEngine.getGame().getPlayers().get(playerNr))
                                    .flatMap(player -> {
                                        kafkaSender.sendSse(messageEvent(new MessageEvent().message(new UserMessage().userId(userId).message("Er is verzaakt in slag " + laatsteCompleteSlag + " door " + player.getDisplayName()).variant(UserMessage.VariantEnum.ERROR)), Set.of(userId)));
                                        return Mono.empty();
                                    })
                                ).then();
                        }
                    });
            })
            .then(Mono.empty());
    }


}
