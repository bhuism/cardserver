package nl.appsource.cardserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;

import java.time.Instant;
import java.util.Arrays;

@Slf4j
@RequiredArgsConstructor
public class GameEngineImpl implements GameEngine {

    private final String userId;

    private final Game game;

    @SuppressFBWarnings("RC_REF_COMPARISON")
    @Override
    public Game playCard(final Card card) {

        if (isCompleted()) {
            log.warn("playCard() Game {} is already completed", game.getId());
            return game;
        }

        if (game.getTurns().stream().anyMatch((c) -> c == card)) {
            log.warn("Card already played: {}", card);
            return game;
        }


        final Integer playerNum = Arrays.asList(game.getPlayers()).indexOf(userId);

        if (game.getPlayerCard().get(card) != playerNum) {
            log.warn("Player {} does not have card {}", userId, card);
            return game;
        }


        // FIXME: add check: is speler aan slag?

        game.setUpdated(Instant.now());
        game.getTurns().add(card);
        game.setEnded(isCompleted());

        return game;

    }

    private boolean isCompleted() {
        return game.getTurns().size() >= 32;
    }
}
