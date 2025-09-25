package nl.appsource.cardserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Rank;
import nl.appsource.cardserver.model.Suit;
import nl.appsource.cardserver.service.exception.GameEngineException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class AiPlayer {

    private final GameEngine gameEngine;

    // Main entry point for the AI's decision
    public Card calcAiCard(final String userId) throws GameEngineException {
        final List<Card> hand = getHand(userId);
        final List<Card> currentTrick = gameEngine.getTrickCards(gameEngine.calcTricksPlayed());

        if (currentTrick.isEmpty()) {
            return playAsLeader(hand);
        } else {
            return playAsFollower(userId, hand, currentTrick);
        }
    }

    /**
     * Logic for when the AI is the first to play in a trick.
     */
    private Card playAsLeader(final List<Card> hand) {
        final Suit trumpSuit = gameEngine.getGame().getTrump();
        final Map<Suit, List<Card>> cardsBySuit = hand.stream().collect(Collectors.groupingBy(Card::getSuit));
        final List<Card> trumpCards = cardsBySuit.getOrDefault(trumpSuit, List.of());

        // Strategy 1: "Trump hunting". If you have a strong trump suit, lead a high trump
        // to exhaust the opponents' trumps.
        boolean hasTrumpJack = trumpCards.stream().anyMatch(c -> c.getRank() == Rank.JACK);
        if (trumpCards.size() >= 4 && hasTrumpJack) {
            // Lead with the highest trump you have.
            return trumpCards.stream().max(this::compareKlaverjassenCards).orElseThrow();
        }

        // Strategy 2: Lead with a safe, high-value non-trump card.
        // e.g., an Ace from a suit where we also have the 10.
        for (Map.Entry<Suit, List<Card>> entry : cardsBySuit.entrySet()) {
            if (entry.getKey() != trumpSuit) {
                boolean hasAce = entry.getValue().stream().anyMatch(c -> c.getRank() == Rank.ACE);
                boolean hasTen = entry.getValue().stream().anyMatch(c -> c.getRank() == Rank.TEN);
                if (hasAce && hasTen) {
                    return entry.getValue().stream().filter(c -> c.getRank() == Rank.ACE).findFirst().orElseThrow();
                }
            }
        }

        // Strategy 3: Lead a singleton non-trump card to create a void.
        Optional<Card> singletonPlay = cardsBySuit.entrySet().stream()
            .filter(entry -> entry.getKey() != trumpSuit)
            .filter(entry -> entry.getValue().size() == 1)
            .map(entry -> entry.getValue().get(0))
            .findFirst();
        if (singletonPlay.isPresent()) {
            return singletonPlay.get();
        }

        // Strategy 4: Play the lowest card of the shortest non-trump suit.
        // This is a safe play to get rid of a suit and allow trumping sooner.
        Optional<Card> safePlay = hand.stream()
            .filter(c -> c.getSuit() != trumpSuit)
            .min(Comparator.<Card>comparingInt(c -> getCardsOfSuit(hand, c.getSuit()).size()) // Prefer shorter suits
                .thenComparing(this::getKlaverjassenValue)); // Then play lowest card of that suit
        if (safePlay.isPresent()) {
            return safePlay.get();
        }

        // Strategy 5: Only trumps are left, must lead with a trump.
        // Lead with the highest trump to try and win the trick.
        return hand.stream()
            .max(this::compareKlaverjassenCards)
            .orElseThrow();
    }

    /**
     * Logic for when the AI is following another player.
     */
    private Card playAsFollower(final String userId, final List<Card> hand, final List<Card> currentTrick) {
        final String partnerId = gameEngine.getPartner(userId); // Assumed method
        final String currentWinnerId = gameEngine.getTrickWinnerId(currentTrick); // Assumed method

        // Key strategic decision: Is my partner winning?
        // If it's the last trick, always try to win for the +10 points.
        if (currentWinnerId.equals(partnerId) && !gameEngine.isLastTrick()) {
            return playToSupportPartner(hand, currentTrick.getFirst().getSuit());
        } else {
            return playToWin(hand, currentTrick);
        }
    }

    /**
     * Play to win the trick because the opponent or the AI itself is currently winning.
     */
    private Card playToWin(final List<Card> hand, final List<Card> currentTrick) {
        final Suit leadingSuit = currentTrick.getFirst()
            .getSuit();
        final Suit trumpSuit = gameEngine.getGame()
            .getTrump();
        final Card highestCardInTrick = getHighestCardInTrick(currentTrick);

        // Rule 1: Must follow suit if possible.
        if (hasSuit(hand, leadingSuit)) {
            final List<Card> playableCards = getCardsOfSuit(hand, leadingSuit);
            // Must try to win if possible ("overkennen")
            Optional<Card> winningCard = playableCards.stream()
                .filter(c -> compareKlaverjassenCards(c, highestCardInTrick) > 0)
                .min(this::compareKlaverjassenCards); // Play the LOWEST card that still wins

            // If a winning card is available, play it. Otherwise, play the lowest card of the suit.
            return winningCard.orElse(playableCards.stream()
                .min(this::compareKlaverjassenCards)
                .orElseThrow());
        }

        // Rule 2: Cannot follow suit, must trump if possible (and lead is not trump).
        if (leadingSuit != trumpSuit && hasSuit(hand, trumpSuit)) {
            final List<Card> trumpCards = getCardsOfSuit(hand, trumpSuit);
            // Must try to over-trump if possible.
            Optional<Card> overTrumpCard = trumpCards.stream()
                .filter(c -> compareKlaverjassenCards(c, highestCardInTrick) > 0)
                .min(this::compareKlaverjassenCards); // Play the LOWEST trump that wins

            // If you can over-trump, you must. Otherwise, you must under-trump.
            return overTrumpCard.orElse(trumpCards.stream()
                .min(this::compareKlaverjassenCards)
                .orElseThrow());
        }

        // Rule 3: Cannot follow suit and cannot trump. Discard a card.
        // Use signaling to inform the partner about a strong suit.
        return discardCardWithSignal(hand);
    }

    /**
     * Play to support a partner who is already winning the trick.
     * Rules for "Amsterdam" Klaverjassen are followed here.
     */
    private Card playToSupportPartner(final List<Card> hand, final Suit leadingSuit) {
        final Suit trumpSuit = gameEngine.getGame().getTrump();

        // Rule 1: Must follow suit if possible.
        if (hasSuit(hand, leadingSuit)) {
            // Strategy: Play the card with the HIGHEST point value to "grease" the trick for your partner.
            return getCardsOfSuit(hand, leadingSuit).stream()
                .max(Comparator.comparingInt(this::getStandardPointValue))
                .orElseThrow();
        }

        // Rule 2: Cannot follow suit. Must trump if you can.
        if (hasSuit(hand, trumpSuit)) {
            // When partner is winning, the goal is to not waste a high trump.
            // Play the lowest trump card you have. This is called "onder-trumpen".
            return getCardsOfSuit(hand, trumpSuit).stream()
                .min(this::compareKlaverjassenCards)
                .orElseThrow();
        }

        // Rule 3: Cannot follow suit and cannot trump. Discard a card.
        // Use signaling to inform the partner about a strong suit.
        return discardCardWithSignal(hand);
    }

    /**
     * Discards a card, attempting to signal a strong suit to the partner.
     * A signal is made by discarding a low-value card from a suit where the AI holds an Ace or Ten.
     */
    private Card discardCardWithSignal(final List<Card> hand) {
        final Suit trumpSuit = gameEngine.getGame().getTrump();
        final Map<Suit, List<Card>> nonTrumpCardsBySuit = hand.stream()
            .filter(c -> c.getSuit() != trumpSuit)
            .collect(Collectors.groupingBy(Card::getSuit));

        // Strategy 1: Signal a strong suit.
        // Find a suit where we have an Ace or Ten, and discard a low card from it.
        Optional<Card> signalCard = nonTrumpCardsBySuit.entrySet().stream()
            .filter(entry -> {
                // A suit is "strong" if we have a high card (Ace or Ten) in it.
                boolean hasAce = entry.getValue().stream().anyMatch(c -> c.getRank() == Rank.ACE);
                boolean hasTen = entry.getValue().stream().anyMatch(c -> c.getRank() == Rank.TEN);
                return hasAce || hasTen;
            })
            .map(entry -> entry.getValue().stream().min(this::compareKlaverjassenCards).orElse(null)) // Get the lowest card of that strong suit
            .filter(java.util.Objects::nonNull)
            .min(this::compareKlaverjassenCards); // From the possible signal cards, pick the lowest one overall.

        if (signalCard.isPresent()) {
            return signalCard.get();
        }

        // Strategy 2: No strong suit to signal. Discard the lowest-value non-trump card.
        Optional<Card> lowestNonTrump = hand.stream()
            .filter(c -> c.getSuit() != trumpSuit)
            .min(this::compareKlaverjassenCards);

        if (lowestNonTrump.isPresent()) {
            return lowestNonTrump.get();
        }

        // Strategy 3: Only trumps are left. Must discard the lowest trump.
        return hand.stream()
            .min(this::compareKlaverjassenCards)
            .orElseThrow();
    }


    // --- HELPER AND UTILITY METHODS ---

    public Card getHighestCardInTrick(final List<Card> trick) {
        return trick.stream()
            .max(this::compareKlaverjassenCards)
            .orElseThrow();
    }

    private List<Card> getHand(final String userId) {
        final int playerNum = gameEngine.getGame()
            .getPlayers()
            .indexOf(userId);
        return gameEngine.getGame()
            .getPlayerCard()
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue()
                .equals(playerNum))
            .map(Map.Entry::getKey)
            .filter(card -> !gameEngine.getGame()
                .getTurns()
                .contains(card))
            .sorted(this::compareKlaverjassenCards) // Good practice to keep hand sorted
            .collect(Collectors.toList());
    }

    private boolean hasSuit(final List<Card> hand, final Suit suit) {
        return hand.stream()
            .anyMatch(card -> card.getSuit() == suit);
    }

    private List<Card> getCardsOfSuit(final List<Card> hand, final Suit suit) {
        return hand.stream()
            .filter(card -> card.getSuit() == suit)
            .collect(Collectors.toList());
    }

    /**
     * Compares cards based on their game-winning value (trump > non-trump).
     */
    private int compareKlaverjassenCards(final Card c1, final Card c2) {
        return Integer.compare(getKlaverjassenValue(c1), getKlaverjassenValue(c2));
    }

    /**
     * Gets the Klaverjassen power-value of a card (e.g., Trump Jack is highest).
     */
    private int getKlaverjassenValue(final Card card) {
        boolean isTrump = card.getSuit()
            .equals(gameEngine.getGame()
                .getTrump());
        return isTrump ? card.getRank().getTrumpValue() : card.getRank().getStandardValue();
    }

    /**
     * Gets the standard point value of a card for scoring and "smeren" (Ace=11, 10=10, etc.).
     */
    private int getStandardPointValue(final Card card) {
        return card.getRank().getStandardValue();
    }

    // The bidding threshold is an estimate of how many points the AI thinks it can make with its partner.
    // A typical game has 162 points available, so a team needs 82 to win.
    // A bid is a commitment to take more than half the points.
    // This threshold represents confidence in the hand's strength. A value around 35-40 is common.
    private static final int BIDDING_THRESHOLD = 38;

    public boolean decideBid(final String userId) {
        final List<Card> hand = getHand(userId);
        final Suit trumpSuit = gameEngine.getGame().getTrump();
        int handStrength = 0;

        final Map<Suit, List<Card>> cardsBySuit = hand.stream()
            .collect(Collectors.groupingBy(Card::getSuit));

        // 1. Evaluate trump suit strength
        final List<Card> trumpCards = cardsBySuit.getOrDefault(trumpSuit, List.of());
        final boolean hasTrumpJack = trumpCards.stream().anyMatch(c -> c.getRank() == Rank.JACK);
        final boolean hasTrumpNine = trumpCards.stream().anyMatch(c -> c.getRank() == Rank.NINE);

        if (hasTrumpJack) handStrength += 12; // The highest trump is a massive advantage.
        if (hasTrumpNine) handStrength += 8;  // The second highest trump is also key.

        // Bonus for having multiple high trumps
        if (hasTrumpJack && hasTrumpNine) handStrength += 5;

        // Bonus for length of the trump suit. Control of the game.
        if (trumpCards.size() >= 4) {
            handStrength += (trumpCards.size() - 3) * 5;
        }

        // Check for "Stuk" (King and Queen of trump)
        final boolean hasTrumpKing = trumpCards.stream().anyMatch(c -> c.getRank() == Rank.KING);
        final boolean hasTrumpQueen = trumpCards.stream().anyMatch(c -> c.getRank() == Rank.QUEEN);
        if (hasTrumpKing && hasTrumpQueen) {
            handStrength += 5; // This is worth 20 points if declared, but also indicates trump strength.
        }

        // 2. Evaluate non-trump suits for point-making potential
        for (Map.Entry<Suit, List<Card>> entry : cardsBySuit.entrySet()) {
            if (entry.getKey() == trumpSuit) continue;

            List<Card> suitCards = entry.getValue();
            boolean hasAce = suitCards.stream().anyMatch(c -> c.getRank() == Rank.ACE);
            boolean hasTen = suitCards.stream().anyMatch(c -> c.getRank() == Rank.TEN);

            if (hasAce) handStrength += 7; // A non-trump Ace is often a guaranteed trick.
            if (hasTen) handStrength += 4; // A non-trump Ten is a likely point winner.
            if (hasAce && hasTen) handStrength += 3; // Ace-Ten combo is very strong.
        }

        // 3. Evaluate distribution for voiding/trumping potential
        for (Suit s : Suit.values()) {
            if (s == trumpSuit) continue;
            int suitSize = cardsBySuit.getOrDefault(s, List.of()).size();
            if (suitSize == 1) {
                handStrength += 3; // Singleton allows for trumping after one round.
            } else if (suitSize == 0) {
                handStrength += 6; // Void allows for immediate trumping.
            }
        }

        final boolean decision = handStrength >= BIDDING_THRESHOLD;
        log.info("{}: evaluates their hand for trump: {}, with strength: {}, decision={}", userId, trumpSuit, handStrength, decision);
        return decision;
    }
}
