package nl.appsource.cardserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.GameToOpenApiConverter;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.Suit;
import nl.appsource.cardserver.repository.GameRepository;
import nl.appsource.cardserver.service.exception.GameEngineException;
import org.openapitools.model.MessageEvent;
import org.openapitools.model.PlayCardResponse;
import org.openapitools.model.UserMessage;
import org.springframework.data.domain.Sort;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.annotation.Scheduled;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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

    private final GameToOpenApiConverter gameToOpenApiConverter;

    private static final Random RAND = new SecureRandom();

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
        if (isAdmin(userId)) {
            return gameRepository.findAll(Sort.by(Sort.Direction.DESC, "updated"));
        } else {
            return gameRepository.findByUserId(userId);
        }
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

        return Mono.just(new nl.appsource.cardserver.model.Game()).doOnNext((game) -> {
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
        }).flatMap(gameRepository::save)
            .doOnNext((game) -> sseEmitterRepository.gamesChanged(game.getPlayers()))
            .doOnNext(sseEmitterRepository::newGame);

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
        return gameRepository.findById(gameId).flatMap((g) -> {
            final int cardOwnerIndex = g.getPlayerCard().get(card);
            final String playerId = g.getPlayers().get(cardOwnerIndex);
            try {
                new GameEngineImpl(g).playCard(playerId, card).forEach(this::sendUserMessage);
                return gameRepository.save(g)
                    .doOnNext(this::sendGameChangedEvent)
                    .doOnNext(game -> finishTrickWithAi(game.getId()))
                    .doOnNext(game -> sseEmitterRepository.gamesChanged(game.getPlayers()))
                    .map((_g) -> new PlayCardResponse().cardWasPlayed(true));
            } catch (GameEngineException e) {
                return Mono.error(e);
            }
        });
    }

    @Override
    public Mono<Void> say(final String userId, final String gameId, final Boolean say) {
        return gameRepository.findById(gameId)
            .flatMap(g -> {
                try {
                    new GameEngineImpl(g).say(userId, say).forEach(this::sendUserMessage);
                    return gameRepository.save(g)
                        .doOnNext(this::sendGameChangedEvent)
                        .doOnNext(game -> finishTrickWithAi(game.getId()))
                        .doOnNext(game -> sseEmitterRepository.gamesChanged(game.getPlayers()));
                } catch (GameEngineException e) {
                    return Mono.error(e);
                }
            }).then(Mono.empty());
    }

    protected void finishTrickWithAi(final String gameId) {
        gameRepository.findById(gameId).subscribe((game) -> {

            final GameEngine gameEngine = new GameEngineImpl(game);
            final int currentTrickNr = gameEngine.calcTricksPlayed();

            if (!gameEngine.hasFullTrick() && gameEngine.isAiTurn()) {
                Mono.just(gameId).delayElement(Duration.ofSeconds(2))
                    .flatMap(g -> _playSomeExtraAi(gameId, currentTrickNr)).delayElement(Duration.ofSeconds(2))
                    .flatMap(g -> _playSomeExtraAi(gameId, currentTrickNr)).delayElement(Duration.ofSeconds(2))
                    .flatMap(g -> _playSomeExtraAi(gameId, currentTrickNr))
                    .subscribe();
            }
        });
    }

    private Mono<String> _playSomeExtraAi(final String gameId, final int trickNr) {
        return gameRepository.findById(gameId).flatMap((g) -> {
            final GameEngine gameEngine = new GameEngineImpl(g);
            if (!gameEngine.hasFullTrick() && gameEngine.calcTricksPlayed() == trickNr && gameEngine.isAiTurn()) {
                try {
                    gameEngine.playAiCard();
                    return gameRepository.save(g)
                        .doOnNext(this::sendGameChangedEvent)
                        .doOnNext(game -> sseEmitterRepository.gamesChanged(game.getPlayers()))
                        .map(Game::getId);
                } catch (GameEngineException e) {
                    log.error("Exception during playAiCard()", e);
                    return Mono.error(e);
                }
            } else {
                return Mono.empty();
            }
        });
    }

    @Override
    public Mono<Void> kickAi(final String userId, final String gameId) {
        return gameRepository.findById(gameId).flatMap((game) -> {

            final GameEngine gameEngine = new GameEngineImpl(game);

            try {

                if (gameEngine.isAiSay()) {
                    gameEngine.sayAi();
                    return gameRepository.save(game).doOnNext(this::sendGameChangedEvent);
                }

                if (gameEngine.isAiTurn()) {
                    gameEngine.playAiCard();
                    return gameRepository.save(game).doOnNext(this::sendGameChangedEvent);
                }

                return Mono.just(game);


            } catch (GameEngineException e) {
                log.error("Exception during sayAi()", e);
                return Mono.error(e);
            }


        }).doOnNext(game -> {
            finishTrickWithAi(game.getId());
        }).then();

    }


    public static Map<Card, Integer> randomCards() {
        final Map<Card, Integer> cards = new HashMap<>();
        final List<Card> deck = Arrays.asList(Card.values());
        shuffle(deck, RAND);
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

    private final Sinks.Many<ServerSentEvent<Object>> gameSink = Sinks.many().multicast().onBackpressureBuffer(1, false);

    @Scheduled(fixedDelay = 1000 * 5, initialDelay = 1000 * 5)
    public void pingAll() {
        this.ping();
    }


    private void ping() {
        internalSend(createPing());
    }

    private ServerSentEvent<Object> createPing() {
        return createServerSentEvent("gamePing");
    }

    @Override
    public void sendGameChangedEvent(final Game game) {
        internalSend(createServerSentEvent("gameStateUpdate", gameToOpenApiConverter.convert(game)));
    }

    @Override
    public void sendUserMessage(final UserMessage userMessage) {
        internalSend(createServerSentEvent("gameMessageEvent", new MessageEvent().message(userMessage)));
    }

    private void internalSend(final ServerSentEvent<Object> serverSentEvent) {
        gameSink.tryEmitNext(serverSentEvent);
    }

    private ServerSentEvent<Object> createServerSentEvent(final String event) {
        return createServerSentEvent(event, null);
    }

    private ServerSentEvent<Object> createServerSentEvent(final String event, final Object data) {
        final Instant now = Instant.now();
        final String id = "" + (now.getEpochSecond() * 1000000 + now.getNano());
        return ServerSentEvent.builder().event(event).id(id).data(data == null ? "{}" : data).build();
    }

    @Override
    public Flux<ServerSentEvent<Object>> gameStream(final String userId, final String gameId) {
        return Flux.just(createPing(), createPing(), createPing()).concatWith(gameSink.asFlux().doOnSubscribe((a) -> log.info("subscribe() userId={} gameId={} count={}", userId, gameId, gameSink.currentSubscriberCount())).doOnCancel(() -> log.info("unSubscribe() userId={} gameId={} count={}", userId, gameId, gameSink.currentSubscriberCount())).filter(sse -> {
            assert sse.event() != null;
            return switch (sse.event()) {
                case "gamePing" -> true;
                case "gameMessageEvent" -> true;
                case "gameStateUpdate" -> sse.data() != null && gameId.equals(((org.openapitools.model.Game) sse.data()).getId());
                default -> {
                    log.error("Unknown event: {}", sse.event());
                    yield false;
                }
            };
        }));
    }

}
