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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static java.util.Collections.shuffle;
import static java.util.Collections.singleton;
import static nl.appsource.cardserver.service.GameEngineImpl.AI_USER_ID;
import static nl.appsource.cardserver.service.GameEngineImpl.isAiPlayer;
import static nl.appsource.cardserver.utils.Utils.isAdmin;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServiceImpl implements GameService {

    private final GameRepository gameRepository;

    private final Environment environment;

    private final SseEmitterRepository sseEmitterRepository;

    private static final Random RAND = new SecureRandom();
    private final UserRepository userRepository;

    @Override
    public Mono<Game> getGame(final String userId, final String gameId) {
        if (isAdmin(userId)) {
            return gameRepository.findById(gameId);
        } else {
            return gameRepository.findByUserIdAndGameId(userId, gameId);
        }
    }

    @Override
    public Flux<Game> getGames(final String userId) {
        return gameRepository.findByUserId(userId);
    }

    @Override
    public Mono<Game> createGame(final String creator, final Set<String> players) {

        if (players.isEmpty()) {
            throw new IllegalArgumentException("need at least one player");
        }

        if (!StringUtils.hasText(creator)) {
            throw new IllegalArgumentException("creator cannot be empty");
        }

        if (!players.contains(creator)) {
            throw new IllegalArgumentException("creator needs to be a player");
        }

        while (players.size() < 4) {
            players.add(AI_USER_ID.get(players.size() - 1));
        }

        final List<String> randomizedOrderPlayers = new ArrayList<>(players);

        shuffle(randomizedOrderPlayers, RAND);

        log.info("Creating a new game with players {}", randomizedOrderPlayers);

        return userRepository.findById(creator)
            .flatMap((user) -> Mono.just(new Game())
                .doOnNext((game) -> {
                    game.setId(idGen(20));
                    game.setCreator(creator);
                    game.setCreated(Instant.now());
                    game.setUpdated(Instant.now());
                    game.setPlayers(randomizedOrderPlayers);
                    game.setDealer(RAND.nextInt(4));
                    game.setSay(new HashMap<>());
                    game.setTurns(new ArrayList<>());
                    game.setPlayerCard(randomCards());
                    game.setTrump(Suit.values()[RAND.nextInt(Suit.values().length)]);
                    game.setLastTrickOpen(false);
                    game.setGameVariant(user.getGameVariant());
                    game.setDealCounter(0);
                })
                .flatMap(gameRepository::save)
                .doOnNext((game) -> sseEmitterRepository.gamesChanged(game.getPlayers()))
                .doOnNext(sseEmitterRepository::newGame)
                .doOnNext(game -> {
                    if (new GameEngineImpl(game).isAiSay()) {
                        scheduleGameEvent(new ScheduledGameEvent(System.currentTimeMillis() + 5000, null, GameEventType.AI_SAY, game.getId()));
                    }
                })

            );

    }

    @Override
    public Mono<Boolean> deleteGame(final String userId, final String gameId) {
        synchronized (lockMap.computeIfAbsent(gameId, k -> new Object())) {
            return gameRepository.findById(gameId)
                .filter(game -> game.getCreator()
                    .equals(userId))
                .flatMap(game -> gameRepository.delete(game)
                    .then(Mono.fromRunnable(() -> sseEmitterRepository.gamesChanged(game.getPlayers())))
                    .thenReturn(true)
                );
        }
    }

    private final ConcurrentMap<String, Object> lockMap = new ConcurrentHashMap<>();

    private final PriorityQueue<ScheduledGameEvent> eventQueue = new PriorityQueue<>(Comparator.comparingLong(ScheduledGameEvent::getExecutionTime));

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    boolean stop = false;

    @PostConstruct
    public void init() {

        scheduler.scheduleWithFixedDelay(this::processDueEvents, 1000, 250, TimeUnit.MILLISECONDS);

        if (environment.acceptsProfiles(Profiles.of("production"))) {
            gameRepository.findAll()
                .map(GameEngineImpl::new)
                .flatMap(gameEngine -> {

                    if (gameEngine.isCompleted()) {
                        return Mono.empty();
                    }

                    if (gameEngine.getGame()
                        .getLastTrickOpen()) {
                        gameEngine.getGame()
                            .setLastTrickOpen(false);
                    }
                    return Mono.just(gameEngine.game());
                })
                .flatMap(gameRepository::save)
                .map(GameEngineImpl::new)
                .flatMap(gameEngine -> {

                    if (gameEngine.isAiSay()) {
                        final String userId = gameEngine.getGame()
                            .getPlayers()
                            .get(gameEngine.calcWhoSay());
                        this.executeSynchronious(GameEventType.AI_SAY, userId, gameEngine.getGame()
                            .getId(), null, null);
                        return Mono.just(gameEngine.getGame());
                    } else if (gameEngine.isAiTurn()) {
                        final String userId = gameEngine.getGame()
                            .getPlayers()
                            .get(gameEngine.calcWhoHasTurn());
                        this.executeSynchronious(GameEventType.AI_PLAY_CARD, userId, gameEngine.getGame()
                            .getId(), null, null);
                        return Mono.just(gameEngine.getGame());
                    }

                    return Mono.empty();
                })
                .subscribe(gameRepository::save);
        }
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

    public synchronized void processDueEvents() {
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
        try {
            return gameEngineExecutor.run();
        } catch (Throwable throwable) {
            return Mono.error(throwable);
        }

    }

    private void executeSynchronious(final GameEventType gameEventType, final String userId, final String gameId, final Card card, final Boolean say) {

        synchronized (lockMap.computeIfAbsent(gameId, k -> new Object())) {
            log.info("Executing: {} for game {} userId: {}", gameEventType, gameId, userId);
            Mono.just(gameId)
                .flatMap(gid -> userId == null || isAiPlayer(userId) ? gameRepository.findById(gid) : gameRepository.findByUserIdAndGameId(userId, gid))
                .map(GameEngineImpl::new)
                .filter(gameEngine -> !gameEngine.isCompleted())
                .flatMap(gameEngine -> switch (gameEventType) {
                    case AI_SAY -> catchException(gameEngine::sayAi);
                    case AI_PLAY_CARD -> catchException(gameEngine::playAiCard);
                    case OPEN_LAST_TRICK -> catchException(gameEngine::openLastTrick);
                    case CLOSE_LAST_TRICK -> catchException(gameEngine::closeLastTrick);
                    case HUMAN_PLAY_CARD -> catchException(() -> gameEngine.playCard(userId, card));
                    case HUMAN_SAY -> catchException(() -> gameEngine.say(userId, say));
                })
                .doOnNext(gameEngine -> gameEngine.getGame().setUpdated(Instant.now()))
                .flatMap(gameEngine -> gameRepository.save(gameEngine.getGame()).then(Mono.just(gameEngine)))
                .doOnNext(gameEngine -> sseEmitterRepository.updateGameStateAllPlayers(gameEngine.getGame()))
                .subscribe(gameEngine -> {

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

                }, throwable -> {
                    sseEmitterRepository.sendMessage(singleton(userId), new UserMessage().userId(userId).variant(UserMessage.VariantEnum.ERROR).message(throwable.getClass().getName() + ":" + throwable.getMessage()));
                });

        }
    }


    @Override
    public void scheduleGameEvent(final ScheduledGameEvent scheduledGameEvent) {
        eventQueue.add(scheduledGameEvent);
        this.processDueEvents();
    }

    public static Map<Card, Integer> randomCards() {
        final Map<Card, Integer> cards = new HashMap<>();
        final List<Card> deck = Arrays.asList(Card.values());
        shuffle(deck, RAND);
        IntStream.range(0, deck.size())
            .forEach(index -> cards.put(deck.get(index), index % 4));
        return cards;
    }

    public static String idGen(final int length) {
        final String characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        final StringBuilder result = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int index = RAND.nextInt(characters.length());
            result.append(characters.charAt(index));
        }

        return result.toString();
    }

    @Override
    public Mono<Void> reload(final UUID appIdentifier, final String userId, final String gameId) {
        return gameRepository.findByUserIdAndGameId(userId, gameId)
            .doOnNext(game -> sseEmitterRepository.updateGameStateForId(appIdentifier, game))
            .then();
    }

    @Override
    public Mono<Void> claimRoem(final UUID appIdentifier, final String userId, final String gameId) {
        synchronized (lockMap.computeIfAbsent(gameId, k -> new Object())) {
            return gameRepository.findById(gameId)
                .map(GameEngineImpl::new)
                .flatMap(gameEngine -> {
                    final int slagNr = gameEngine.calcTricksPlayed();

                    final int correctedSlagNr = slagNr - (slagNr > 0 && gameEngine.getTurnCount() % 4 == 0 ? 1 : 0);

                    final int roem = gameEngine.calculateTrickRoem(correctedSlagNr);
                    if (roem > 0) {
                        final boolean result = gameEngine.getGame().getRoemGeklopt().add(gameEngine.calcTricksPlayed());
                        if (result) {
                            sseEmitterRepository.sendMessage(gameEngine.getGame().getPlayers(), new UserMessage().userId(userId).message("Er is " + roem + " roem geklopt in slag " + (correctedSlagNr + 1)).variant(UserMessage.VariantEnum.INFO));
                            return gameRepository.save(gameEngine.getGame()).doOnNext(game -> sseEmitterRepository.updateGameStateAllPlayers(gameEngine.getGame())).map(a -> gameEngine);
                        } else {
                            return Mono.empty();
                        }
                    } else {
                        sseEmitterRepository.sendAppIdentifierMessage(appIdentifier, new UserMessage().userId(userId).message("Er is geen roem in slag " + (correctedSlagNr + 1)).variant(UserMessage.VariantEnum.WARNING));
                        return Mono.empty();
                    }
                })
                .then();
        }
    }

    @Override
    public Mono<Void> gameMessage(final String userId, final String gameId, final String message) {
        return gameRepository.findByUserIdAndGameId(userId, gameId)
            .doOnNext(game -> sseEmitterRepository.sendMessage(game.getPlayers(), new UserMessage().userId(userId).message(message).variant(UserMessage.VariantEnum.INFO)))
            .then();
    }

    @Override
    public Mono<Void> claimVerzaken(final UUID appIdentifier, final String userId, final String gameId) {
        synchronized (lockMap.computeIfAbsent(gameId, k -> new Object())) {
            return gameRepository.findById(gameId)
                .map(GameEngineImpl::new)
                .flatMap(gameEngine -> {
                    final int slagNr = gameEngine.calcTricksPlayed();

                    final int correctedSlagNr = slagNr - (slagNr > 0 && gameEngine.getTurnCount() % 4 == 0 ? 1 : 0);

                    final AtomicBoolean atLeastOne = new AtomicBoolean(false);

                    List.of(0, 1, 2, 3).forEach(playerNr -> {
                        final Boolean verzaakt = gameEngine.verzaakt(correctedSlagNr, playerNr);
                        if (verzaakt) {
                            sseEmitterRepository.sendMessage(gameEngine.getGame().getPlayers(), new UserMessage().userId(userId).variant(UserMessage.VariantEnum.ERROR).message("Er is verzaakt in slag " + correctedSlagNr + " door speler " + playerNr));
                            atLeastOne.set(true);
                        }
                    });

                    if (!atLeastOne.get()) {
                        sseEmitterRepository.sendMessage(gameEngine.getGame().getPlayers(), new UserMessage().userId(userId).variant(UserMessage.VariantEnum.ERROR).message("Er is niet verzaakt in slag " + correctedSlagNr));
                    }

                    return Mono.just(gameEngine);

                })
                .then();
        }
    }


}
