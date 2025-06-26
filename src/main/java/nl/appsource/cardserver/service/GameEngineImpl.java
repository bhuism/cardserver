package nl.appsource.cardserver.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;

import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
public class GameEngineImpl implements GameEngine {

    public static class GameEngineException extends RuntimeException {
        private GameEngineException() {
        }

        private GameEngineException(final String message) {
            super(message);
        }
    }

    @Getter
    @RequiredArgsConstructor
    public static final class GameCompletedException extends GameEngineException {
        private final String gameId;
    }

    @Getter
    @RequiredArgsConstructor
    public static final class CardAlreadyPlayerException extends GameEngineException {
        private final String gameId;
        private final Card card;
    }

    private final String userId;

    private final Game game;

    @Override
    public Game playCard(final Card card)  {

        if (isCompleted()) {
            throw new GameCompletedException(game.getId());
        }

        if (game.getTurns().stream().anyMatch((c) -> c == card)) {
            throw new CardAlreadyPlayerException(game.getId(), card);
        }

//        final Integer playerNum = game.getPlayers().indexOf(userId);
//
//        final Integer howHasCard = game.getPlayerCard().get(card);
//
//        if (howHasCard != playerNum) {
//            log.warn("Player {} does not have card {}, player {} does", userId, card, howHasCard);
//            return game;
//        }


        // FIXME: add check: is speler aan slag?

        game.setUpdated(Instant.now());
        game.getTurns().add(card);

        return game;

    }

    private boolean isCompleted() {
        return game.getTurns().size() >= 32;
    }
}
