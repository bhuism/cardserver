package nl.appsource.cardserver.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.Rank;
import nl.appsource.cardserver.model.Suit;
import nl.appsource.cardserver.service.exception.CardAlreadyPlayerException;
import nl.appsource.cardserver.service.exception.GameCompletedException;
import nl.appsource.cardserver.service.exception.GameEngineException;
import nl.appsource.cardserver.service.exception.NotAPlayerException;
import nl.appsource.cardserver.service.exception.NotPlayersTurnException;
import org.openapitools.model.UserMessageMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

@Slf4j
public class GameEngineImpl implements GameEngine {

    public static final List<String> AI_USER_ID = List.of("2ab5fd69a2796c4740380cd98eb7", "2ab5fd69a2796c4740380cd98eb8", "2ab5fd69a2796c4740380cd98eb9");

//    private final String userId;

    @Getter
    private final Game game;

    //  private final List<Card> hand = new ArrayList<>();

    // private final Integer playerNum;

    private static final Map<Rank, Integer> RANK_REGULAR = Map.of(Rank.Ace, 8, Rank.King, 6, Rank.Queen, 5, Rank.Jack, 4, Rank.Ten, 7, Rank.Nine, 3, Rank.Eight, 2, Rank.Seven, 1);

    private static final Map<Rank, Integer> RANK_TRUMP = Map.of(Rank.Ace, 14, Rank.King, 12, Rank.Queen, 11, Rank.Jack, 16, Rank.Ten, 13, Rank.Nine, 15, Rank.Eight, 10, Rank.Seven, 9);

    private static final Comparator<? super Card> TRUMP_SORTER = comparing(o -> RANK_TRUMP.get(o.getRank()));

    private static final Comparator<? super Card> REGULAR_SORTER = comparing(o -> RANK_REGULAR.get(o.getRank()));

    public GameEngineImpl(final Game gameArg) {
        this.game = gameArg;
    }

    private int calcTricksPlayed() {
        return this.game.getTurns().size() / 4;
    }

    @Override
    public boolean hasFullTrick() {
        return this.game.getTurns().size() >= 4 && this.game.getTurns().size() % 4 == 0;
    }

    private List<Card> getTrickCards(final int trickNr) {
        log.info("getTrickcards() {}", trickNr);
        return game.getTurns().subList(trickNr * 4, Math.min(game.getTurns().size(), trickNr * 4 + 4));
    }

    private Card determineTrickWinningCard(final int trickNr) {

        if (trickNr >= calcTricksPlayed() || trickNr < 0) {
            throw new GameEngineException("no such trick " + trickNr, UserMessageMessage.VariantEnum.ERROR);
        }

        final List<Card> trick = getTrickCards(trickNr);

        if (trick.size() != 4) {
            throw new GameEngineException("Not 5 trick cards in tick " + trickNr, UserMessageMessage.VariantEnum.ERROR);
        }

        final boolean troefAanwezig = trick.stream().anyMatch(c -> c.getSuit().equals(game.getTrump()));

        final Suit requestedSuit = trick.getFirst().getSuit();

        return trick.stream().filter(c -> c.getSuit().equals(troefAanwezig ? game.getTrump() : requestedSuit)).max(troefAanwezig ? TRUMP_SORTER : REGULAR_SORTER).orElseThrow(() -> new GameEngineException("No card found", UserMessageMessage.VariantEnum.ERROR));

    }

    final int determineTrickWinner(final int trickNr) {
        final Card winningCard = determineTrickWinningCard(trickNr);
        return whoHasCard(winningCard);
    }

    @Override
    public int calcWhoHasTurn() {

        final int laatsteKaart = game.getTurns().size() % 4;

        if (laatsteKaart != 0) {
            return (whoHasCard(game.getTurns().getLast()) + 1) % 4;
        } else {
            if (game.getTurns().isEmpty()) {
                return (game.getDealer() + 1) % 4;
            } else {

                final int tricksPlayedCount = calcTricksPlayed();
                return determineTrickWinner(tricksPlayedCount - 1) + game.getTurns().size() % 4;

            }
        }
    }

    @Override
    public void playCard(final String userId, final Card card) {

        final int playerNum = this.game.getPlayers().indexOf(userId);

        if (isCompleted()) {
            log.warn("Game {} allready completed", game.getId());
            throw new GameCompletedException();
        }

        if (!game.getPlayers().contains(userId)) {
            throw new NotAPlayerException();
        }

        final int cardOwner = whoHasCard(card);

        if (playerNum != cardOwner) {
            log.warn("Player does not have card {}", card);
        }

        if (game.getTurns().stream().anyMatch((c) -> c == card)) {
            log.warn("Card {} allready played", card);
            throw new CardAlreadyPlayerException(card);
        }

        final int gotTurn = calcWhoHasTurn();

        if (gotTurn != playerNum) {
            log.warn("playCard({}) It's player {} turn", card, game.getPlayers().get(gotTurn));
            throw new NotPlayersTurnException();
        }

        log.info("playCard() game: {}, card: {}, player: {}", game.getId(), card, userId);

        game.setUpdated(Instant.now());
        game.getTurns().add(card);

    }

    @Override
    public boolean isCompleted() {
        return game.getTurns().size() >= 32;
    }

    private int whoHasCard(final Card card) {
        return game.getPlayerCard().get(card);
    }

    private static boolean hasSuit(final List<Card> hand, final Suit suit) {
        return hand.stream().map(Card::getSuit).anyMatch(handCardSuit -> handCardSuit.equals(suit));
    }

    private static List<Card> getCardsOfSuit(final List<Card> hand, final Suit suit) {
        return hand.stream().filter(card -> card.getSuit().equals(suit)).collect(Collectors.toList());
    }

    private Integer getKlaverjassenValue(final Card c1) {
        return c1.getSuit().equals(game.getTrump()) ? RANK_TRUMP.get(c1.getRank()) : RANK_REGULAR.get(c1.getRank());
    }

    private int compareKlaverjassenCards(final Card card1, final Card card2) {
        return Integer.compare(getKlaverjassenValue(card1), getKlaverjassenValue(card2));
    }


    public Card getHighestCardInTrick(final List<Card> trick) {
        if (trick.isEmpty()) {
            return null;
        }
        return trick.stream().max(this::compareKlaverjassenCards).orElseThrow(() -> new GameEngineException("Can not find highest card in trick", UserMessageMessage.VariantEnum.ERROR));
    }

//    @Override
//    public void playAiCard() {
//
//        final int gotTurn = calcWhoHasTurn();
//
//        final String aiUserId = game.getPlayers().get(gotTurn);
//
//        if (!AI_USER_ID.contains(aiUserId)) {
//            throw new GameEngineException("Player who is aan slag is not an AI player", UserMessage.VariantEnum.ERROR);
//        }
//
//        final Card card = calcAiCard(aiUserId);
//
//        log.info("Ai {} plays card {}", aiUserId, card);
//
//        playCard(aiUserId, card);
//
//    }

    @Override
    public Card calcAiCard(final String userId) {

        log.info("calcAiCard() userId={}", userId);

        if (!AI_USER_ID.contains(userId)) {
            throw new GameEngineException("Not an Ai player", UserMessageMessage.VariantEnum.ERROR);
        }

        final int playerNum = this.game.getPlayers().indexOf(userId);

        final boolean isFirstPlayerInTrick = game.getTurns().size() % 4 == 0;

        log.info("isFirstPlayerInTrick={}", isFirstPlayerInTrick);

        final List<Card> hand = game.getPlayerCard().entrySet().stream().filter(cardIntegerEntry -> cardIntegerEntry.getValue().equals(playerNum)).map(Map.Entry::getKey).filter(card -> !game.getTurns().contains(card)).toList();

        log.info("hande={}", hand);

        // If leading the trick, play the lowest card of a non-trump suit, or lowest trump if only trumps.
        if (isFirstPlayerInTrick) {
            // Try to play a low card from a non-trump suit
            for (Card card : hand) {
                if (card.getSuit() != game.getTrump() && card.getRank() != Rank.Seven && card.getRank() != Rank.Eight) {
                    return card;
                }
            }
            // If only trumps or high cards, play the lowest card
            return hand.getFirst();
        } else {

            final List<Card> currentTrick = getTrickCards(calcTricksPlayed());


            // Not the first player, must follow suit if possible
            Card leadingCard = currentTrick.get(0);
            Suit leadingSuit = leadingCard.getSuit();

            List<Card> playableCards = new ArrayList<>();

            // Rule 1: Must follow suit if possible
            if (hasSuit(hand, leadingSuit)) {
                List<Card> cardsOfLeadingSuit = getCardsOfSuit(hand, leadingSuit);
                // Try to beat the current highest card in the trick if possible and not trump
                Card highestInTrick = getHighestCardInTrick(currentTrick);

                for (Card card : cardsOfLeadingSuit) {
                    // If leading suit is trump, or if card can beat the highest non-trump card
                    if (leadingSuit == game.getTrump() || compareKlaverjassenCards(card, highestInTrick) > 0) {
                        playableCards.add(card);
                    }
                }

                if (!playableCards.isEmpty()) {
                    // Play the lowest card that can beat the current highest, or lowest if none can beat
                    Collections.sort(playableCards, (c1, c2) -> {
                        int val1 = getKlaverjassenValue(c1);
                        int val2 = getKlaverjassenValue(c2);
                        return Integer.compare(val1, val2);
                    });
                    // Prioritize beating the card if possible, otherwise play lowest of suit
                    for (Card card : playableCards) {
                        if (compareKlaverjassenCards(card, highestInTrick) > 0) {
                            return card; // Play a card that beats
                        }
                    }
                    return playableCards.get(0); // Play lowest if cannot beat
                } else {
                    // If has suit but no card can beat, play the lowest of the leading suit
                    Collections.sort(cardsOfLeadingSuit, (c1, c2) -> {
                        int val1 = getKlaverjassenValue(c1);
                        int val2 = getKlaverjassenValue(c2);
                        return Integer.compare(val1, val2);
                    });
                    return cardsOfLeadingSuit.get(0);
                }
            } else {
                // Cannot follow suit
                // Rule 2: If leading suit is not trump, and player has trump, must trump if possible
                if (leadingSuit != game.getTrump() && hasSuit(hand, game.getTrump())) {
                    List<Card> trumpCards = getCardsOfSuit(hand, game.getTrump());
                    Card highestInTrick = getHighestCardInTrick(currentTrick);

                    // Try to play a trump that can beat the current highest card in the trick
                    for (Card trumpCard : trumpCards) {
                        if (compareKlaverjassenCards(trumpCard, highestInTrick) > 0) {
                            playableCards.add(trumpCard);
                        }
                    }

                    if (!playableCards.isEmpty()) {
                        // Play the lowest trump that can beat
                        Collections.sort(playableCards, (c1, c2) -> {
                            int val1 = getKlaverjassenValue(c1);
                            int val2 = getKlaverjassenValue(c2);
                            return Integer.compare(val1, val2);
                        });
                        return playableCards.get(0);
                    } else {
                        // If has trump but cannot beat, play the lowest trump
                        Collections.sort(trumpCards, (c1, c2) -> {
                            int val1 = getKlaverjassenValue(c1);
                            int val2 = getKlaverjassenValue(c2);
                            return Integer.compare(val1, val2);
                        });
                        return trumpCards.get(0);
                    }
                } else {
                    // Cannot follow suit and cannot trump (or leading suit is trump and cannot follow)
                    // Rule 3: Discard a low card (preferably from a non-trump suit)
                    for (Card card : hand) {
                        if (card.getSuit() != game.getTrump()) {
                            return card; // Play a non-trump card
                        }
                    }
                    // If only trumps left, play the lowest trump
                    return hand.get(0);
                }
            }
        }
    }


}
