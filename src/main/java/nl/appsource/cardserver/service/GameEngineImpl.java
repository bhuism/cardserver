package nl.appsource.cardserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;

import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
public class GameEngineImpl implements GameEngine {

    private final String userId;

    private final Game game;

    @Override
    public Game playCard(final Card card) {
        if (isCompleted()) {
            log.warn("playCard() Game {} is already completed", game.getId());
            return game;
        }

        // FIXME: heeft speler kaart nog wel?

        // FIXME: add check: is speler aan slag?

        game.setUpdated(Instant.now());
        game.getTurns().add(card);

        return game;

    }

    private boolean isCompleted() {
        return game.getTurns().size() >= 8;
    }
}
