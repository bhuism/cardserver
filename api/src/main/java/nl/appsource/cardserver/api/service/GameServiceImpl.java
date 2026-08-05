package nl.appsource.cardserver.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converters.service.GameToOpenApiConverter;
import nl.appsource.cardserver.couchbase.repository.BoomRepository;
import nl.appsource.cardserver.couchbase.repository.GameRepository;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.model.AiRisc;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.GameVariant;
import nl.appsource.cardserver.model.Suit;
import nl.appsource.cardserver.openapi.config.KafkaTopics;
import nl.appsource.cardserver.openapi.service.KafkaSender;
import nl.appsource.cardserver.openapi.service.SseEventSender;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

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

    private final GameToOpenApiConverter gameToOpenApiConverter;

    private final KafkaSender kafkaSender;

    private final JsonMapper jsonMapper;

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

//        log.info("Creating a new game with players {}", players);

        return Mono.just(new Game())
            .doOnNext((game) -> {
                game.setId(idGen(GAME, 20));
                game.setPlayers(new ArrayList<>(players));
                game.setDealer(dealer == null ? Integer.valueOf(ThreadLocalRandom.current()
                    .nextInt(4)) : dealer);
                game.setSay(new HashMap<>());
                game.setTurns(new ArrayList<>());
                game.setPlayerCard(randomCards());
                game.setTrump(Suit.values()[ThreadLocalRandom.current()
                    .nextInt(Suit.values().length)]);
                game.setLastTrickOpen(false);
                game.setGameVariant(gameVariant);
                game.setDealCounter(0);
                game.setBoomId(boomId);
                game.setAiRisc(aiRisc);
            })
            .flatMap(gameRepository::save)
            .flatMap((game) -> sseEventSender.gamesChanged(concat(game.getPlayers()
                    .stream(), Stream.of(game.getCreator())).collect(toSet()))
                .then(Mono.just(game)))
            .doOnNext((game) -> kafkaSender.send(KafkaTopics.GAME_CHANGES, jsonMapper.writeValueAsString(game)))
            .delayUntil(game -> sseEventSender.newGame(gameToOpenApiConverter.convert(game)));

    }

    @Override
    public Mono<Boolean> deleteGame(final String userId, final String gameId) {
        return gameRepository.findById(gameId)
            .filter(game -> game.getCreator()
                .equals(userId))
            .filter(game -> game.getBoomId() == null)
            .flatMap(game -> gameRepository.delete(game)
                .then(sseEventSender.gamesChanged(concat(game.getPlayers()
                    .stream(), Stream.of(game.getCreator())).collect(toSet()))))
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
