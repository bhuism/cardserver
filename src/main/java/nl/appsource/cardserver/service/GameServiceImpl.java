package nl.appsource.cardserver.service;

import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.Suit;
import nl.appsource.cardserver.repository.GameRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Math.abs;

@Service
@RequiredArgsConstructor
public class GameServiceImpl implements GameService {

    private final GameRepository gameRepository;

    private final SseEmitterRepository sseEmitterRepository;

    private static final Random RAND = new SecureRandom();

    @Override
    public Optional<Game> getGame(final String userId, final String gameId) {
        return gameRepository.findByUserIdAndGameId(userId, gameId);
    }

    @Override
    public List<Game> getGames(final String userId) {
        return gameRepository.findByUserId(userId).stream().toList();
    }

    @Override
    public Game createGame(final String creator, final Set<String> players) {

        if (players.size() != 4) {
            throw new IllegalArgumentException("players count must be 4");
        }

        if (StringUtils.isBlank(creator)) {
            throw new IllegalArgumentException("creator cannot be empty");
        }

        final nl.appsource.cardserver.model.Game game = new nl.appsource.cardserver.model.Game();

        game.setId(idGen(20));
        game.setCreator(creator);
        game.setCreated(Instant.now());
        game.setUpdated(Instant.now());
        game.setPlayers(new ArrayList<>(players));
        game.setDealer(0);
        game.setElder(abs(RAND.nextInt()) % 4);
        game.setTurns(new ArrayList<>());
        game.setPlayerCard(randomCards());
        game.setTrump(Suit.Clubs);

        final nl.appsource.cardserver.model.Game savedGame = gameRepository.save(game);

        sseEmitterRepository.gamesChanged(players.stream().filter((p) -> !Objects.equals(p, creator)).collect(Collectors.toSet()));

        return savedGame;
    }

    @Override
    public void deleteGame(final String gameId) {
        gameRepository.deleteById(gameId);
    }

    @Override
    public Game playCard(final String userId, final Game game, final Card card) {
        final Game newGame = gameRepository.save(new GameEngineImpl(userId, game).playCard(card));

        // distribute event
        sseEmitterRepository.playCard(userId, game.getId(), card);
        return newGame;
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
