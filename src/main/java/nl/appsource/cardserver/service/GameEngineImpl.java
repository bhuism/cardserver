package nl.appsource.cardserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.CardNr;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.Suit;
import nl.appsource.cardserver.service.exception.CardAlreadyPlayerException;
import nl.appsource.cardserver.service.exception.GameCompletedException;
import nl.appsource.cardserver.service.exception.NotAPlayerException;
import nl.appsource.cardserver.service.exception.NotPlayersTurnException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static java.util.Comparator.comparing;

@Slf4j
@RequiredArgsConstructor
public class GameEngineImpl implements GameEngine {

    private final String userId;

    private final Game game;

    private static final Map<CardNr, Integer> RANK_REGULAR = Map.of(
        CardNr.Ace, 8,
        CardNr.King, 6,
        CardNr.Queen, 5,
        CardNr.Jack, 4,
        CardNr.Ten, 7,
        CardNr.Nine, 3,
        CardNr.Eight, 2,
        CardNr.Seven, 1
    );

    private static final Map<CardNr, Integer> RANK_TRUMP = Map.of(
        CardNr.Ace, 6,
        CardNr.King, 4,
        CardNr.Queen, 3,
        CardNr.Jack, 8,
        CardNr.Ten, 5,
        CardNr.Nine, 7,
        CardNr.Eight, 2,
        CardNr.Seven, 1
    );

    private static final Comparator<? super Card> TRUMP_SORTER = comparing(o -> RANK_TRUMP.get(o.getCardNr()));

    private static final Comparator<? super Card> REGULAR_SORTER = comparing(o -> RANK_REGULAR.get(o.getCardNr()));


    private int calcTricksPlayed() {
        return this.game.getTurns().size() / 4;
    }

    private List<Card> getTrickCards(final int trickNr) {
        return game.getTurns().subList(trickNr * 4, trickNr * 4 + 4);
    }

    private Card determineTrickWinningCard(final int trickNr) {

        if (trickNr >= calcTricksPlayed() || trickNr < 0) {
            throw new IllegalArgumentException("no such trick " + trickNr);
        }

        final List<Card> trick = getTrickCards(trickNr);

        if (trick.size() != 4) {
            throw new IllegalArgumentException("Not 5 trick cards in tick " + trickNr);
        }

        final boolean troefAanwezig = trick.stream().anyMatch(c -> c.getSuit().equals(game.getTrump()));

        final Suit requestedSuit = trick.getFirst().getSuit();

        return trick.stream().filter(c -> c.getSuit().equals(troefAanwezig ? game.getTrump() : requestedSuit)).min(troefAanwezig ? TRUMP_SORTER : REGULAR_SORTER).orElseThrow(IllegalArgumentException::new);

    }

    final int determineTrickWinner(final int trickNr) {
        final Card winningCard = determineTrickWinningCard(trickNr);
        return whoHasCard(winningCard);
    }


    @Override
    public Game playCard(final Card card) {

        if (isCompleted()) {
            log.warn("Game {} allready completed", game.getId());
            throw new GameCompletedException();
        }

        if (!game.getPlayers().contains(userId)) {
            throw new NotAPlayerException();
        }

        if (game.getTurns().stream().anyMatch((c) -> c == card)) {
            log.warn("Card {} allready played", card);
            throw new CardAlreadyPlayerException(card);
        }

        final int laatsteKaart = game.getTurns().size() % 4;

        final int cardPlayer = whoHasCard(card);

        if (laatsteKaart != 0) {
            final int gotTurn = (whoHasCard(game.getTurns().getLast()) + 1) % 4;
            if (cardPlayer != gotTurn) {
                log.warn("playCard({}) It's player {} turn", card, game.getPlayers().get(gotTurn));
                throw new NotPlayersTurnException();
            }
        } else {
            if (game.getTurns().isEmpty()) {
                final int gotTurn = (game.getDealer() + 1) % 4;
                if (gotTurn != whoHasCard(card)) {
                    log.warn("playCard({}) It's player {} turn", card, game.getPlayers().get(gotTurn));
                    throw new NotPlayersTurnException();
                }
            } else {

                final int tricksPlayedCount = calcTricksPlayed();
                final int gotTurn = determineTrickWinner(tricksPlayedCount - 1) + game.getTurns().size() % 4;

                if (gotTurn != cardPlayer) {
                    log.warn("playCard({}) It's player {} turn", card, game.getPlayers().get(gotTurn));
                    throw new NotPlayersTurnException();

                }
            }
        }

        log.info("playCard() game: {}, card: {}, player: {}", game.getId(), card, userId);

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
