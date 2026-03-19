package nl.appsource.cardsever.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converters.service.GameToOpenApiConverter;
import nl.appsource.cardserver.couchbase.repository.BoomRepository;
import nl.appsource.cardserver.couchbase.repository.GameRepository;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.couchbase.utils.GameEngine;
import nl.appsource.cardserver.couchbase.utils.GameEngineImpl;
import nl.appsource.cardserver.model.AiRisc;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.GameVariant;
import nl.appsource.cardserver.model.Suit;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import nl.appsource.cardserver.openapi.service.RedisPubSubService;
import nl.appsource.cardserver.openapi.service.RedisStreamService;
import nl.appsource.generated.openapi.model.GameEvent;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Collections.shuffle;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;
import static nl.appsource.cardserver.openapi.MyServerSentEvent.updateGame;
import static nl.appsource.cardserver.utils.IDTYPE.GAME;
import static nl.appsource.cardserver.utils.Utils.idGen;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServiceImpl implements GameService {

    private final GameRepository gameRepository;

    private final Environment environment;

    private final UserRepository userRepository;

    private final SseEventSender sseEventSender;

    private final BoomRepository boomRepository;

    private final RedisPubSubService redisPubSubService;

    private final GameToOpenApiConverter gameToOpenApiConverter;

    private final RedisStreamService redisStreamService;

    private Mono<Game> sendUpdateGame(final Game game) {
        final MyServerSentEvent gameEvent = updateGame(gameToOpenApiConverter.convert(game));
        return redisPubSubService.broadCast(Flux.fromIterable(game.getPlayers())
            .mergeWith(Flux.just(game.getCreator(), game.getId())).distinct(), gameEvent).thenReturn(game);
    }

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
                game.setDealer(dealer == null ? Integer.valueOf(ThreadLocalRandom.current().nextInt(4)) : dealer);
                game.setSay(new HashMap<>());
                game.setTurns(new ArrayList<>());
                game.setPlayerCard(randomCards());
                game.setTrump(Suit.values()[ThreadLocalRandom.current().nextInt(Suit.values().length)]);
                game.setLastTrickOpen(false);
                game.setGameVariant(gameVariant);
                game.setDealCounter(0);
                game.setBoomId(boomId);
                game.setAiRisc(aiRisc);
            })
            .flatMap(gameRepository::save)
            .flatMap(this::sendUpdateGame)
            .flatMap((game) -> sseEventSender.gamesChanged(concat(game.getPlayers().stream(), Stream.of(game.getCreator())).collect(toSet())).then(Mono.just(game)))
            .flatMap(game -> sseEventSender.newGame(game).then(Mono.just(game)))
            .flatMap(game -> {
                final GameEngine gameEngine = new GameEngineImpl(game);
                if (gameEngine.isAiSay()) {
                    final String aiSayPlayer = game.getPlayers().get(gameEngine.calcWhoSay());
                    return redisStreamService.publishToStream(aiSayPlayer, new GameEvent().eventType(GameEvent.EventTypeEnum.SAY).gameId(game.getId()).userId(aiSayPlayer)).then(Mono.just(game));
                } else {
                    return Mono.just(game);
                }
            });
    }

    @Override
    public Mono<Boolean> deleteGame(final String userId, final String gameId) {
        return gameRepository.findById(gameId)
            .filter(game -> game.getCreator().equals(userId))
            .filter(game -> game.getBoomId() == null)
            .flatMap(game -> gameRepository.delete(game).then(sseEventSender.gamesChanged(concat(game.getPlayers().stream(), Stream.of(game.getCreator())).collect(toSet()))))
            .thenReturn(true);
    }

    private static Map<Card, Integer> randomCards() {
        final Map<Card, Integer> cards = new HashMap<>();
        final List<Card> deck = Arrays.asList(Card.values());
        shuffle(deck, ThreadLocalRandom.current());
        IntStream.range(0, deck.size())
            .forEach(index -> cards.put(deck.get(index), index % 4));
        return cards;
    }

}
