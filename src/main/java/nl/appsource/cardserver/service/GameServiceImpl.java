package nl.appsource.cardserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.Suit;
import nl.appsource.cardserver.repository.GameRepository;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.exception.GameEngineException;
import org.openapitools.model.PlayCardResponse;
import org.openapitools.model.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static java.util.Collections.shuffle;
import static nl.appsource.cardserver.service.GameEngineImpl.AI_USER_ID;
import static nl.appsource.cardserver.utils.Utils.isAdmin;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServiceImpl implements GameService {

    private final GameRepository gameRepository;

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
                //.doOnNext(game -> finishWithAi(game.getId(), Duration.ofSeconds(1), game.getTurns().size()))
            );

    }

    @Override
    public Mono<Boolean> deleteGame(final String userId, final String gameId) {
        return gameRepository.findById(gameId)
            .filter(game -> game.getCreator()
                .equals(userId))
            .flatMap(game -> gameRepository.delete(game)
                .then(Mono.fromRunnable(() -> sseEmitterRepository.gamesChanged(game.getPlayers())))
                .thenReturn(true));
    }

    @Override
    public Mono<PlayCardResponse> playCard(final UUID appIdentifier, final String userId, final String gameId, final Card card) {
        return gameRepository.findById(gameId)
            .flatMap((g) -> {
                final int cardOwnerIndex = g.getPlayerCard()
                    .get(card);
                final String playerId = g.getPlayers()
                    .get(cardOwnerIndex);
                try {
                    new GameEngineImpl(g).playCard(playerId, card)
                        .forEach(message -> this.sseEmitterRepository.sendAppIdentifierMessage(appIdentifier, message));
                    return gameRepository.save(g)
                        .doOnNext(this::sendGameStateUpdate)
                        .doOnNext(game -> finishWithAi(game.getId(), Duration.ofSeconds(2), game.getTurns()
                            .size()))
                        .map((_g) -> new PlayCardResponse().cardWasPlayed(true));
                } catch (GameEngineException gameEngineException) {
                    sseEmitterRepository.sendAppIdentifierMessage(appIdentifier, new UserMessage().userId(userId)
                        .message(gameEngineException.getMessage())
                        .variant(UserMessage.VariantEnum.ERROR));
                    return Mono.error(gameEngineException);
                } catch (Throwable throwable) {
                    sseEmitterRepository.sendAppIdentifierMessage(appIdentifier, new UserMessage().userId(userId)
                        .message(throwable.getClass()
                            .getName() + ":" + throwable.getMessage())
                        .variant(UserMessage.VariantEnum.ERROR));
                    return Mono.error(throwable);
                }
            });
    }

    @Override
    public Mono<Void> say(final UUID appIdentifier, final String userId, final String gameId, final Boolean say) {
        return gameRepository.findById(gameId)
            .flatMap(g -> {
                try {
                    new GameEngineImpl(g).say(userId, say)
                        .forEach(message -> this.sseEmitterRepository.sendAppIdentifierMessage(appIdentifier, message));
                    return gameRepository.save(g)
                        .doOnNext(this::sendGameStateUpdate)
                        .doOnNext(game -> finishWithAi(game.getId(), Duration.ofSeconds(2), game.getTurns()
                            .size()));
                } catch (GameEngineException e) {
                    return Mono.error(e);
                }
            })
            .then();
    }

    final Set<String> atomicArray = ConcurrentHashMap.newKeySet();


    @Override
    public void finishWithAi(final String gameId, final Duration initialDelay, final int turnCount) {
        if (atomicArray.add(gameId)) {
            gameRepository.findById(gameId)
                .filter(game -> {
                    final GameEngine gameEngine = new GameEngineImpl(game);
                    return (gameEngine.isAiSay() || gameEngine.isAiTurn()) && game.getTurns()
                        .size() == turnCount;
                })
                .delayElement(initialDelay)
                .map(GameEngineImpl::new)
                .filter(gameEngine -> gameEngine.getTurnCount() == turnCount)
                .flatMap(gameEngine -> {
                    try {
                        if (gameEngine.isAiSay()) {
                            gameEngine.sayAi()
                                .forEach(message -> log.warn("{}:{}", message.getVariant(), message.getMessage()));
                            return Mono.just(gameEngine);
                        } else if (gameEngine.isAiTurn()) {
                            gameEngine.playAiCard()
                                .forEach(message -> log.warn("{}:{}", message.getVariant(), message.getMessage()));
                            return Mono.just(gameEngine);
                        } else {
                            return Mono.empty();
                        }
                    } catch (GameEngineException e) {
                        log.error("Exception during sayAi()", e);
                        return Mono.error(e);
                    }
                })
                .map(GameEngineImpl::getGame)
                .flatMap(gameRepository::save)
                .map(this::sendGameStateUpdate)
                .filter(game -> {
                    final GameEngine gameEngine = new GameEngineImpl(game);
                    return gameEngine.isAiSay() || gameEngine.isAiTurn();
                })
                .doOnNext(game -> this.finishWithAi(gameId, Duration.ofSeconds(2), game.getTurns().size()))
                .doFinally(signalType -> {
                    atomicArray.remove(gameId);
                })
                .subscribe();
        } else {
            log.warn("Game {} already has a stream running", gameId);
        }
    }

//    @Override
//    public Mono<Game> deal(final String userId, final String gameId) {
//        return Mono.defer(() -> {
//            synchronized (gameProcessingMonos) {
//                return gameProcessingMonos.computeIfAbsent(gameId, k ->
//                    gameRepository.findById(gameId)
//                        .filter(game -> game.getCreator().equals(userId))
//                        .flatMap(game -> {
//                            final GameEngine gameEngine = new GameEngineImpl(game);
//                            gameEngine.deal();
//                            return gameRepository.save(game)
//                                .doOnNext(this::sendGameStateUpdate)
//                                .doOnNext(g -> finishWithAi(g.getId(), Duration.ofSeconds(1), g.getTurns().size()))
//                                .doFinally(signalType -> {
//                                    synchronized (gameProcessingMonos) {
//                                        gameProcessingMonos.remove(gameId);
//                                    }
//                                });
//                        })
//                );
//            }
//        });
//    }

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
    public Mono<Void> openLastTrick(final String userId, final String gameId) {
        return gameRepository.findById(gameId)
            .flatMap(g -> {
                g.setLastTrickOpen(g.getLastTrickOpen() == null || !g.getLastTrickOpen());
                return gameRepository.save(g)
                    .doOnNext(this::sendGameStateUpdate);
            })
            .then();
    }

    private Game sendGameStateUpdate(final Game game) {
        sseEmitterRepository.updateGameStateAllPlayers(game);
        return game;
    }

    @Override
    public Mono<Void> reload(final UUID appIdentifier, final String userId, final String gameId) {
        return gameRepository.findByUserIdAndGameId(userId, gameId)
            .doOnNext(game -> {
                sseEmitterRepository.updateGameStateForId(appIdentifier, game);
            })
            .then();
    }
}
