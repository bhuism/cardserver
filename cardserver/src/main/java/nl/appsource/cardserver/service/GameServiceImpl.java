package nl.appsource.cardserver.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.couchbase.model.AiRisc;
import nl.appsource.cardserver.couchbase.model.Card;
import nl.appsource.cardserver.couchbase.model.Game;
import nl.appsource.cardserver.couchbase.model.GameVariant;
import nl.appsource.cardserver.couchbase.model.Suit;
import nl.appsource.cardserver.couchbase.repository.BoomRepository;
import nl.appsource.cardserver.couchbase.repository.GameRepository;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.service.event.ScheduledGameEvent;
import nl.appsource.cardserver.utils.CardServerAuthentication;
import nl.appsource.generated.openapi.model.UserMessage;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.lang.Math.max;
import static java.lang.Runtime.getRuntime;
import static java.util.Collections.shuffle;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;
import static nl.appsource.cardserver.service.GameEngineImpl.isAiPlayer;
import static nl.appsource.cardserver.utils.IDTYPE.GAME;
import static nl.appsource.cardserver.utils.Utils.idGen;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServiceImpl implements GameService {

    private final GameRepository gameRepository;

    private final Environment environment;

    private static final Random RAND = new SecureRandom();

    private final PriorityQueue<ScheduledGameEvent> eventQueue = new PriorityQueue<>(Comparator.comparingLong(ScheduledGameEvent::getExecutionTime));

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(max(1, getRuntime().availableProcessors() - 1));

    private final UserRepository userRepository;

    private final SseEventSender sseEventSender;

    private final BoomRepository boomRepository;

    boolean stop = false;

    @Override
    public Mono<Game> getGame(final String userId, final String gameId) {
        return gameRepository.findByUserIdAndGameId(userId, gameId);
    }

    @Override
    public Flux<String> getGames(final String userId, final boolean includeBoom, final boolean includeFinished, final Integer limit) {
        return gameRepository.findGameIdsByUserId(userId, includeBoom, includeFinished, limit);
    }

    @Override
    public Mono<Game> createGame(final String creator, final List<String> players, final GameVariant gameVariant, final AiRisc aiRisc) {
        return createGame(creator, players, gameVariant, null, null, aiRisc);
    }

    @Override
    public Mono<Game> createGame(final String creator, final List<String> players, final GameVariant gameVariant, final String boomId, final Integer dealer, final AiRisc aiRisc) {

        if (players.size() != 4) {
            throw new IllegalArgumentException("need 4 players: " + players);
        }

        if (!StringUtils.hasText(creator)) {
            throw new IllegalArgumentException("creator cannot be empty");
        }

        if (!players.contains(creator)) {
            throw new IllegalArgumentException("creator needs to be a player");
        }

        log.info("Creating a new game with players {}", players);

        return Mono.just(new Game())
            .doOnNext((game) -> {
                game.setId(idGen(GAME, 20));
                game.setPlayers(new ArrayList<>(players));
                game.setDealer(dealer == null ? Integer.valueOf(RAND.nextInt(4)) : dealer);
                game.setSay(new HashMap<>());
                game.setTurns(new ArrayList<>());
                game.setPlayerCard(randomCards());
                game.setTrump(Suit.values()[RAND.nextInt(Suit.values().length)]);
                game.setLastTrickOpen(false);
                game.setGameVariant(gameVariant);
                game.setDealCounter(0);
                game.setBoomId(boomId);
                game.setAiRisc(aiRisc);
            })
            .flatMap(gameRepository::save)
            .flatMap((game) -> sseEventSender.gamesChanged(concat(game.getPlayers().stream(), Stream.of(game.getCreator())).collect(toSet())).then(Mono.just(game)))
            .flatMap(game -> sseEventSender.newGame(game).then(Mono.just(game)))
            .doOnNext(game -> scheduleNext(new GameEngineImpl(game)));
    }

    @Override
    public Mono<Boolean> deleteGame(final String userId, final String gameId) {
        return gameRepository.findById(gameId)
            .filter(game -> game.getCreator().equals(userId))
            .filter(game -> game.getBoomId() == null)
            .flatMap(game -> gameRepository.delete(game).then(sseEventSender.gamesChanged(concat(game.getPlayers().stream(), Stream.of(game.getCreator())).collect(toSet()))))
            .thenReturn(true);
    }

    @PostConstruct
    public void init() {
        log.info("init()");
        if (environment.acceptsProfiles(Profiles.of("production", "development"))) {
            gameRepository.findAll()
                .filter((game) -> game.getTurns().size() != 32)
                .map(Game::getId)
                .subscribe(gameId -> {
                    executeSynchronious(GameEventType.CLOSE_LAST_TRICK, null, gameId, null, null);
                    executeSynchronious(GameEventType.CHECK_ROTATE, null, gameId, null, null);
                    gameRepository.findById(gameId)
                        .map(GameEngineImpl::new)
                        .subscribe(this::scheduleNext);
                });
        }
        scheduler.scheduleWithFixedDelay(this::processDueEvents, 5000, 500, TimeUnit.MILLISECONDS);
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
        while (!eventQueue.isEmpty() && eventQueue.peek()
            .getExecutionTime() <= currentTime) {
            final ScheduledGameEvent eventToExecute = eventQueue.poll();
            if (eventToExecute != null) {
                try {
                    executeSynchronious(eventToExecute.getGameEventType(), eventToExecute.getUserId(), eventToExecute.getGameId(), eventToExecute.getCard(), eventToExecute.getSay());
                } catch (Throwable t) {
                    log.error("Dont exception in a worker thread", t);
                }
            }
        }
    }

    public Mono<GameEngine> catchException(final GameEngineExecutor gameEngineExecutor) {
        return gameEngineExecutor.run();
    }

    public void executeSynchronious(final GameEventType gameEventType, final String userId, final String gameId, final Card card, final Boolean say) {

        if (userId == null && gameEventType != GameEventType.CHECK_ROTATE && gameEventType != GameEventType.CLOSE_LAST_TRICK) {
            log.error("userId === null, gameEventType=" + gameEventType.name());
        }


        eventQueue.removeIf(scheduledGameEvent -> scheduledGameEvent.getGameId().equals(gameId));

        Mono.just(gameId)
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
                        .map(GameEngineImpl::new)
                        .filter(gameEngine -> !gameEngine.isCompleted())
                        .flatMap(gameEngine -> {

                            final Mono<GameEngine> result = switch (gameEventType) {
                                case AI_SAY -> catchException(gameEngine::sayAi);
                                case AI_PLAY_CARD -> catchException(gameEngine::playAiCard);
                                case OPEN_LAST_TRICK -> catchException(gameEngine::openLastTrick);
                                case CLOSE_LAST_TRICK -> catchException(gameEngine::closeLastTrick);
                                case HUMAN_PLAY_CARD -> catchException(() -> gameEngine.playCard(userId, card));
                                case HUMAN_SAY -> catchException(() -> gameEngine.say(userId, say));
                                case CHECK_ROTATE -> catchException(gameEngine::checkNiemandIsGegaanEnIedereenHeeftGezegd);
                                case CLAIM_ROEM -> catchException(() -> gameEngine.claimRoem(userId, sseEventSender));
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
                    sseEventSender.sendUserIdMessage(userId, throwable.getClass().getName() + ":" + throwable.getMessage(), UserMessage.VariantEnum.ERROR).subscribe();
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

//        final SingleEvent singleEvent = new SingleEvent();
//        singleEvent.setId(idGen(IDTYPE.SVNT, 20));
//        singleEvent.setEvent(scheduledGameEvent.getGameEventType().name());
//        singleEventRepository.save(singleEvent).subscribe();


        eventQueue.add(scheduledGameEvent);
    }

//    public static Map<Card, Integer> randomCards() {
//        final Map<Card, Integer> cards = new HashMap<>();
//        final List<Card> deck = Arrays.asList(Card.values());
//        shuffle(deck, RAND);
//        IntStream.range(0, deck.size())
//            .forEach(index -> cards.put(deck.get(index), index % 4));
//        return cards;
//    }

//    @Override
//    public Mono<Void> reload(final String appIdentifier, final String userId, final String gameId) {
//        return gameRepository.findByUserIdAndGameId(userId, gameId)
//            .doOnNext(game -> sseEmitterRepository.updateGameForId(appIdentifier, game))
//            .then();
//    }

    @Override
    public Mono<Void> claimVerzaken(final CardServerAuthentication auth, final String gameId) {
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
                            return sseEventSender.sendUserIdsMessage(Set.copyOf(gameEngine.getGame()
                                .getPlayers()), "Er is niet verzaakt in slag " + laatsteCompleteSlag, UserMessage.VariantEnum.INFO);
                        } else {
                            return Flux.fromIterable(verzaakteSpelers)
                                .flatMap(playerNr -> userRepository.findById(gameEngine.getGame().getPlayers().get(playerNr))
                                    .flatMap(player -> sseEventSender.sendUserIdsMessage(Set.copyOf(gameEngine.getGame().getPlayers()), "Er is verzaakt in slag " + laatsteCompleteSlag + " door " + player.getDisplayName(), UserMessage.VariantEnum.ERROR))
                                ).then();
                        }
                    });
            });
    }


}
