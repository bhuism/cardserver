package nl.appsource.cardserver.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.Suit;
import nl.appsource.cardserver.repository.GameRepository;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.event.ScheduledGameEvent;
import org.openapitools.model.GameVariant;
import org.openapitools.model.UserMessage;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static java.lang.Math.max;
import static java.lang.Runtime.getRuntime;
import static java.util.Collections.shuffle;
import static java.util.Collections.singleton;
import static nl.appsource.cardserver.service.GameEngineImpl.isAiPlayer;
import static nl.appsource.cardserver.utils.IDTYPE.GAME;
import static nl.appsource.cardserver.utils.Utils.idGen;
import static nl.appsource.cardserver.utils.Utils.isAdmin;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServiceImpl implements GameService {

    private final GameRepository gameRepository;

    private final Environment environment;

    private final SseEmitterRepository sseEmitterRepository;

    private static final Random RAND = new SecureRandom();

    private final PriorityQueue<ScheduledGameEvent> eventQueue = new PriorityQueue<>(Comparator.comparingLong(ScheduledGameEvent::getExecutionTime));

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(max(1, getRuntime().availableProcessors() - 1));

    private final UserRepository userRepository;

    boolean stop = false;

    @Override
    public Mono<Game> getGame(final String userId, final String gameId) {
        if (isAdmin(userId)) {
            return gameRepository.findById(gameId);
        } else {
            return gameRepository.findByUserIdAndGameId(userId, gameId);
        }
    }

    @Override
    public Flux<String> getGames(final String userId, final boolean includeBoom, final boolean includeFinished, final Integer limit) {
        return gameRepository.findGameIdsByUserId(userId, includeBoom, includeFinished, limit);
    }

    @Override
    public Mono<Game> createGame(final String creator, final List<String> players, final GameVariant gameVariant) {
        return createGame(creator, players, gameVariant, null, null);
    }

    @Override
    public Mono<Game> createGame(final String creator, final List<String> players, final GameVariant gameVariant, final String boomId, final Integer dealer) {

        if (players.size() != 4) {
            throw new IllegalArgumentException("need 4 players");
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
            })
            .flatMap(gameRepository::save)
            .doOnNext((game) -> sseEmitterRepository.gamesChanged(game.getPlayers()))
            .doOnNext(sseEmitterRepository::newGame)
            .doOnNext(game -> {
                if (new GameEngineImpl(game).isAiSay()) {
                    scheduleGameEvent(new ScheduledGameEvent(System.currentTimeMillis() + 5000, null, GameEventType.AI_SAY, game.getId()));
                }
            });


    }

    @Override
    public Mono<Boolean> deleteGame(final String userId, final String gameId) {
        return gameRepository.findById(gameId)
            .filter(game -> game.getCreator().equals(userId))
            .filter(game -> game.getBoomId() == null)
            .flatMap(game -> gameRepository.delete(game)
                .then(Mono.fromRunnable(() -> sseEmitterRepository.gamesChanged(game.getPlayers())))
                .thenReturn(true)
            );
    }

    @PostConstruct
    public void init() {
        log.info("init()");
        if (environment.acceptsProfiles(Profiles.of("production"))) {
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

    private void executeSynchronious(final GameEventType gameEventType, final String userId, final String gameId, final Card card, final Boolean say) {

            log.info("Executing locked : {} for game {} userId: {}", gameEventType, gameId, userId);
            eventQueue.removeIf(scheduledGameEvent -> scheduledGameEvent.getGameId().equals(gameId));
            Mono.just(gameId)
                .flatMap(gid -> userId == null || isAiPlayer(userId) ? gameRepository.findById(gid) : gameRepository.findByUserIdAndGameId(userId, gid))
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
                    };

                    return result.doOnNext(_unused -> gameEngine.getGame().setUpdated(Instant.now())).flatMap(_unused -> gameRepository.save(gameEngine.getGame()).then(Mono.just(gameEngine)))
                        .doOnError(throwable -> {
                            sseEmitterRepository.sendMessage(singleton(userId), new UserMessage().userId(userId)
                                .variant(UserMessage.VariantEnum.ERROR)
                                .message(throwable.getClass()
                                    .getName() + ":" + throwable.getMessage()));
                        })
                        .doFinally((_unused) -> this.scheduleNext(gameEngine));
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
        eventQueue.add(scheduledGameEvent);
    }

    public static Map<Card, Integer> randomCards() {
        final Map<Card, Integer> cards = new HashMap<>();
        final List<Card> deck = Arrays.asList(Card.values());
        shuffle(deck, RAND);
        IntStream.range(0, deck.size())
            .forEach(index -> cards.put(deck.get(index), index % 4));
        return cards;
    }

    @Override
    public Mono<Void> reload(final UUID appIdentifier, final String userId, final String gameId) {
        return gameRepository.findByUserIdAndGameId(userId, gameId)
            .doOnNext(game -> sseEmitterRepository.updateGameForId(appIdentifier, game))
            .then();
    }

    @Override
    public Mono<Void> claimRoem(final UUID appIdentifier, final String userId, final String gameId) {
            return gameRepository.findById(gameId)
                .map(GameEngineImpl::new)
                .flatMap(gameEngine -> {
                    final int slagNr = gameEngine.calcTricksPlayed();

                    final int correctedSlagNr = slagNr - (slagNr > 0 && gameEngine.getTurnCount() % 4 == 0 ? 1 : 0);

                    final int roem = gameEngine.calculateTrickRoem(correctedSlagNr);
                    if (roem > 0) {
                        final boolean result = gameEngine.getGame()
                            .getRoemGeklopt()
                            .add(gameEngine.calcTricksPlayed());
                        if (result) {
                            sseEmitterRepository.sendMessage(gameEngine.getGame()
                                .getPlayers(), new UserMessage().userId(userId)
                                .message("Er is " + roem + " roem geklopt in slag " + (correctedSlagNr + 1))
                                .variant(UserMessage.VariantEnum.INFO));
                            return gameRepository.save(gameEngine.getGame())
//                                .doOnNext(_ -> sseEmitterRepository.updateGameState(gameEngine.getGame()))
                                .map(_unused -> gameEngine);
                        } else {
                            return Mono.empty();
                        }
                    } else {
                        sseEmitterRepository.sendAppIdentifierMessage(appIdentifier, new UserMessage().userId(userId)
                            .message("Er is geen roem in slag " + (correctedSlagNr + 1))
                            .variant(UserMessage.VariantEnum.WARNING));
                        return Mono.empty();
                    }
                })
                .then();
    }

    @Override
    public Mono<Void> gameMessage(final String userId, final String gameId, final String message) {
        return gameRepository.findByUserIdAndGameId(userId, gameId)
            .doOnNext(game -> sseEmitterRepository.sendMessage(game.getPlayers(), new UserMessage().userId(userId)
                .message(message)
                .variant(UserMessage.VariantEnum.INFO)))
            .then();
    }

    @Override
    public Mono<Void> claimVerzaken(final UUID appIdentifier, final String userId, final String gameId) {
            return gameRepository.findById(gameId)
                .map(GameEngineImpl::new)
                .flatMap(gameEngine -> {
                    final int slagNr = gameEngine.calcTricksPlayed();

                    final int laatsteCompleteSlag = slagNr - (slagNr > 0 && gameEngine.getTurnCount() % 4 == 0 ? 1 : 0);

                    return Flux.just(0, 1, 2, 3)
                        .flatMap(playerNr -> {
                            final Boolean verzaakt = gameEngine.verzaakt(laatsteCompleteSlag, playerNr);
                            if (verzaakt) {
                                return userRepository.findById(gameEngine.getGame().getPlayers().get(playerNr)).map((player) -> {
                                    sseEmitterRepository.sendMessage(gameEngine.getGame()
                                        .getPlayers(), new UserMessage().userId(userId)
                                        .variant(UserMessage.VariantEnum.ERROR)
                                        .message("Er is verzaakt in slag " + laatsteCompleteSlag + " door " + player.getDisplayName()));
                                    return player;
                                });
                            } else {
                                return Mono.empty();
                            }
                        })
                        .next()
                        .switchIfEmpty(Mono.fromRunnable(() -> {
                            sseEmitterRepository.sendMessage(gameEngine.getGame()
                                .getPlayers(), new UserMessage().userId(userId)
                                .variant(UserMessage.VariantEnum.INFO)
                                .message("Er is niet verzaakt in slag " + laatsteCompleteSlag));
                        }))
                        .then();
                });
    }


}
