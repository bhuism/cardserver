package nl.appsource.cardserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.service.exception.CardAlreadyPlayerException;
import nl.appsource.cardserver.service.exception.GameCompletedException;
import nl.appsource.cardserver.service.exception.NotPlayersTurnException;

import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
public class GameEngineImpl implements GameEngine {

    private final String userId;

    private final Game game;

    @Override
    public Game playCard(final Card card) {

        if (isCompleted()) {
            log.warn("Game {} allready completed", game.getId());
            throw new GameCompletedException(game.getId());
        }

        if (game.getTurns().stream().anyMatch((c) -> c == card)) {
            log.warn("Card {} allready played", card);
            throw new CardAlreadyPlayerException(game.getId(), card);
        }

        final int laatsteKaart = game.getTurns().size() % 4;
        if (laatsteKaart % 4 != 0) {
            final int cardPlayer = whoHasCard(card);
            final int gotTurn = (whoHasCard(game.getTurns().getLast()) + 1) % 4;
            if (cardPlayer != gotTurn) {
                log.warn("It's player {} turn, not {}", gotTurn, cardPlayer);
                throw new NotPlayersTurnException();
            }
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

    private int whoHasCard(final Card card) {
        return game.getPlayerCard().get(card);
    }
}
