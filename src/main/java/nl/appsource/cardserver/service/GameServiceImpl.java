package nl.appsource.cardserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.GameToOpenApiConverter;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.Suit;
import nl.appsource.cardserver.repository.GameRepository;
import nl.appsource.cardserver.service.exception.GameEngineException;
import org.openapitools.model.PlayCardResponse;
import org.openapitools.model.UserMessage;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import static nl.appsource.cardserver.service.GameEngineImpl.AI_USER_ID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServiceImpl implements GameService {

    private final GameRepository gameRepository;

    private final SseEmitterRepository sseEmitterRepository;

    private final GameToOpenApiConverter gameToOpenApiConverter;

    private static final Random RAND = new SecureRandom();

    @Override
    public Mono<Game> getGame(final String userId, final String gameId) {
        return gameRepository.findByUserIdAndGameId(userId, gameId);
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

        while (players.size() < 4) {
            players.add(AI_USER_ID.get(players.size() - 1));
        }

        return Mono.just(new nl.appsource.cardserver.model.Game()).doOnNext((game) -> {
            game.setId(idGen(20));
            game.setCreator(creator);
            game.setCreated(Instant.now());
            game.setUpdated(Instant.now());
            game.setPlayers(new ArrayList<>(players));
            game.setDealer(RAND.nextInt(4));
            game.setElder(RAND.nextInt(4));
            game.setTurns(new ArrayList<>());
            game.setPlayerCard(randomCards());
            game.setTrump(Suit.values()[RAND.nextInt(Suit.values().length)]);
        }).flatMap(gameRepository::save).doOnNext((game) -> sseEmitterRepository.gamesChanged(players)).doOnNext(sseEmitterRepository::newGame);

    }

    @Override
    public Mono<Void> deleteGame(final String gameId) {
        return gameRepository.findById(gameId).flatMap(game -> {
            final List<String> players = game.getPlayers();
            return gameRepository.delete(game).then(Mono.just(players));
        }).flatMap(players -> {
            sseEmitterRepository.gamesChanged(players);
            return Mono.empty();
        });
    }

    @Override
    public Mono<PlayCardResponse> playCard(final String userId, final String gameId, final Card card) {
        try {

            return gameRepository.findById(gameId)
                .flatMap((game) -> {

                    int cardOwnerIndex = game.getPlayerCard().get(card);

                    final String playerId = game.getPlayers().get(cardOwnerIndex);

                    final GameEngine gameEngine = new GameEngineImpl(game);

                    List<UserMessage> userMessages = gameEngine.playCard(playerId, card);

                    if (!gameEngine.hasFullTrick()) {
                        playSomeAi(gameEngine);
                    }

                    return gameRepository.save(gameEngine.getGame()).map(this::gameChanged).map((_g) -> new PlayCardResponse().messages(userMessages).cardWasPlayed(true));
                });
        } catch (GameEngineException e) {
            return Mono.error(e);
        }
    }

    /**
     * @param gameEngine the engine
     * @return if the game was changed
     */

    private boolean playSomeAi(final GameEngine gameEngine) {

        boolean gameWasChanged = false;

        while (!gameEngine.isCompleted() && gameEngine.isAiTurn()) {
            final String aiUserId = gameEngine.getGame().getPlayers().get(gameEngine.calcWhoHasTurn());

//            log.info("Ai " + aiUserId + " is aan slag");
            gameEngine.playCard(aiUserId, gameEngine.calcAiCard(aiUserId)); // we don't user ai user message, he's not a real boy remember, there is no spoon he said

            gameWasChanged = true;

            if (gameEngine.hasFullTrick()) {
                break;
            }
        }

        return gameWasChanged;
    }

    @Override
    public Mono<PlayCardResponse> playAiCard(final String userId, final String gameId) {
        try {

            return gameRepository.findById(gameId)
                .flatMap((game) -> {

                    final GameEngine gameEngine = new GameEngineImpl(game);

                    final boolean gameWasChanged = playSomeAi(gameEngine);

                    if (gameWasChanged) {
                        return gameRepository.save(gameEngine.getGame()).map(this::gameChanged).map((_g) -> new PlayCardResponse().cardWasPlayed(true));
                    } else {
                        return Mono.just(new PlayCardResponse().cardWasPlayed(false));
                    }

                });

        } catch (GameEngineException e) {
            return Mono.error(e);
        }
    }


    public static Map<Card, Integer> randomCards() {
        final Map<Card, Integer> cards = new HashMap<>();
        final List<Card> deck = Arrays.asList(Card.values());
        Collections.shuffle(deck, RAND);
        IntStream.range(0, deck.size()).forEach(index -> cards.put(deck.get(index), index % 4));
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

    private final Sinks.Many<ServerSentEvent<?>> gameSink = Sinks.many().multicast().onBackpressureBuffer(1, false);

    private final Object intervaller = Flux.interval(Duration.ofSeconds(15), Duration.ofSeconds(5)).subscribe(event -> ping());

    private void ping() {
        internalSend("ping", null);
    }

    @Override
    public Game gameChanged(final Game game) {
        internalSend("gameStateUpdate", gameToOpenApiConverter.convert(game));
        return game;
    }

    private void internalSend(final String eventName, final Object data) {
        log.debug("send({}) data={} count={}", eventName, data, gameSink.currentSubscriberCount());
        final Instant now = Instant.now();
        final String id = "" + (now.getEpochSecond() * 1000000 + now.getNano());
        gameSink.tryEmitNext(ServerSentEvent.builder().event(eventName).id(id).data(data != null ? data : "{}").build());
    }

    @Override
    public Flux<ServerSentEvent<?>> gameStream(final String userId, final String gameId) {
        //log.info("subscribe() gameId={} count={}", gameId, gameSink.currentSubscriberCount());

        //ping();
        return gameSink.asFlux()
            .doOnSubscribe((a) -> {
                log.info("subscribe() userId={} gameId={} count={}", userId, gameId, gameSink.currentSubscriberCount());
                ping();
            }).doOnCancel(() -> {
                log.info("unSubscribe() userId={} gameId={} count={}", userId, gameId, gameSink.currentSubscriberCount());
                ping();
            })
            .publish()
            .filter(sse -> {
                if (sse.data() instanceof org.openapitools.model.Game) {
                    return ((org.openapitools.model.Game) sse.data()).getId().equals(gameId);
                } else {
                    return true;
                }
            });

    }

}
