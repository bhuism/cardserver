package nl.appsource.cardserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.Suit;
import nl.appsource.cardserver.repository.GameRepository;
import nl.appsource.cardserver.service.exception.GameEngineException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
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

        return Mono.just(new nl.appsource.cardserver.model.Game())
            .doOnNext((game) -> {
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
            }).flatMap(gameRepository::save)
            .doOnNext((game) -> sseEmitterRepository.gamesChanged(players))
            .doOnNext(sseEmitterRepository::newGame);

    }

    @Override
    public Mono<Void> deleteGame(final String gameId) {
        return gameRepository.findById(gameId)
            .flatMap(game -> {
                final List<String> players = game.getPlayers();
                return gameRepository.delete(game).then(Mono.just(players));
            })
            .flatMap(players -> {
                sseEmitterRepository.gamesChanged(players);
                return Mono.empty();
            });
    }

    @Override
    public Mono<Game> playCard(final String userId, final String gameId, final Card card) {
        try {
            return gameRepository.findById(gameId)
                .flatMap((game) -> gameRepository.save(new GameEngineImpl(userId, game).playCard(card)))
                .map(sseEmitterRepository::gameChanged);
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

}
