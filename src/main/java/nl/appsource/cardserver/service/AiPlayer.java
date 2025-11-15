package nl.appsource.cardserver.service;

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

import static org.openapitools.model.GameVariant.ROTTERDAMS;

@Slf4j
public record AiPlayer(GameEngine gameEngine) {

    private record Hand(List<Card> cards, Map<Suit, List<Card>> bySuit) {
        static Hand from(final List<Card> cards) {
            return new Hand(cards, cards.stream()
                .collect(Collectors.groupingBy(Card::getSuit)));
        }

        List<Card> ofSuit(final Suit suit) {
            return bySuit.getOrDefault(suit, List.of());
        }

        boolean hasSuit(final Suit suit) {
            return bySuit.containsKey(suit);
        }
    }

    // Main entry point for the AI's decision
    public Card calcAiCard(final String userId) throws GameEngineException {
        final Hand hand = Hand.from(getHand(userId));
        final List<Card> currentTrick = gameEngine.getTrickCards(gameEngine.calcTricksPlayed());

        if (currentTrick.isEmpty()) {
            return playAsLeader(hand);
        } else {
            return playAsFollower(userId, hand, currentTrick, getHighestCardInTrick(currentTrick));
        }
    }

    /**
     * Logic for when the AI is the first to play in a trick.
     */
    private Card playAsLeader(final Hand hand) {
        final Suit trumpSuit = gameEngine.getGame()
            .getTrump();
        final List<Card> trumpCards = hand.ofSuit(trumpSuit);

        // Strategy 1: "Trump hunting". If you have a strong trump suit, lead a high trump
        // to exhaust the opponents' trumps.
        boolean hasTrumpJack = trumpCards.stream()
            .anyMatch(c -> c.getRank() == Rank.JACK);
        if (trumpCards.size() >= 5 && hasTrumpJack) { // Be more conservative: only hunt with 5+ trumps including the Jack
            // Lead with the highest trump you have.
            return trumpCards.stream()
                .max(this::compareKlaverjassenCards)
                .orElseThrow();
        }

        // Strategy 2: Lead with a safe, high-value non-trump card.
        // e.g., an Ace from a suit where we also have the 10.
        for (Map.Entry<Suit, List<Card>> entry : hand.bySuit()
            .entrySet()) {
            if (entry.getKey() != trumpSuit) {
                boolean hasAce = entry.getValue()
                    .stream()
                    .anyMatch(c -> c.getRank() == Rank.ACE);
                boolean hasTen = entry.getValue()
                    .stream()
                    .anyMatch(c -> c.getRank() == Rank.TEN);
                if (hasAce && hasTen) {
                    return entry.getValue()
                        .stream()
                        .filter(c -> c.getRank() == Rank.ACE)
                        .findFirst()
                        .orElseThrow(); // This is safe because we've already confirmed hasAce
                }
            }
        }

        // Strategy 3: Lead a singleton non-trump card to create a void.
        Optional<Card> singletonPlay = hand.bySuit()
            .entrySet()
            .stream()
            .filter(entry -> entry.getKey() != trumpSuit)
            .map(Map.Entry::getValue)
            .filter(list -> list.size() == 1)
            .map(List::getFirst)
            .findFirst();
        if (singletonPlay.isPresent()) {
            return singletonPlay.get();
        }

        // Strategy 4: Lead from a long non-trump suit to establish it.
        Optional<Card> longSuitPlay = hand.bySuit()
            .entrySet()
            .stream()
            .filter(entry -> entry.getKey() != trumpSuit)
            .filter(entry -> entry.getValue()
                .size() >= 4) // Find long suits
            .map(entry -> entry.getValue()
                .stream()
                .min(this::compareKlaverjassenCards)) // Get the lowest card
            .flatMap(Optional::stream)
            .findFirst();
        if (longSuitPlay.isPresent()) {
            return longSuitPlay.get();
        }


        // Strategy 5: Play the lowest card of the shortest non-trump suit.
        // This is a safe play, but we should avoid leading an unsupported honor if possible.
        Optional<Card> safePlay = hand.cards()
            .stream()
            .filter(c -> c.getSuit() != trumpSuit)
            .min(Comparator.<Card>comparingInt(c -> {
                    int suitSize = hand.ofSuit(c.getSuit())
                        .size();
                    boolean isUnsupportedHonor = (c.getRank() == Rank.KING || c.getRank() == Rank.QUEEN);
                    // Add a penalty for leading an unsupported King/Queen from a short suit (2 or 3 cards)
                    if (isUnsupportedHonor && suitSize <= 3) {
                        // If the lowest card of this suit is still an honor, it's a very risky lead.
                        if (hand.ofSuit(c.getSuit())
                            .stream()
                            .min(this::compareKlaverjassenCards)
                            .filter(low -> low.getRank() == c.getRank())
                            .isPresent()) {
                            return suitSize + 10; // High penalty to strongly discourage this lead.
                        }
                        return suitSize + 5; // Moderate penalty
                    }
                    return suitSize;
                }) // Prefer shorter suits
                .thenComparing(this::getKlaverjassenValue)); // Then play lowest card of that suit

        // Strategy 6: Only trumps are left, must lead with a trump.
        // Lead with the lowest trump to avoid losing a high trump unnecessarily.
        return safePlay.orElseGet(() -> hand.cards()
            .stream()
            .min(this::compareKlaverjassenCards)
            .orElseThrow()); // Fallback to lowest trump
    }

    /**
     * Logic for when the AI is following another player.
     * This method enforces Klaverjassen rules (following suit, trumping)
     * before applying strategy (winning, smearing, or saving points).
     */
    private Card playAsFollower(final String userId, final Hand hand, final List<Card> currentTrick, final Card highestCardInTrick) {
        final Suit leadingSuit = currentTrick.getFirst().getSuit();
        final Suit trumpSuit = gameEngine.getGame().getTrump();
        final String partnerId = gameEngine.getPartner(userId);
        final String currentWinnerId = gameEngine.getTrickWinnerId(currentTrick);
        final boolean isPartnerWinning = currentWinnerId.equals(partnerId) && !gameEngine.isLastTrick();

        // Rule 1: Must follow suit if possible.
        if (hand.hasSuit(leadingSuit)) {
            final List<Card> playableCards = hand.ofSuit(leadingSuit);
            // If leading suit is trump, you must try to play a higher trump if you can.
            if (leadingSuit == trumpSuit) {
                return playableCards.stream()
                    .filter(c -> compareKlaverjassenCards(c, highestCardInTrick) > 0)
                    .min(this::compareKlaverjassenCards) // Play lowest winning card
                    .orElseGet(() -> playableCards.stream()
                        .min(this::compareKlaverjassenCards)
                        .orElseThrow()); // Or else play lowest card
            } else {
                // Not a trump suit.
                if (isPartnerWinning) {
                    // Partner is winning, so "smear" with high-value cards.
                    return playableCards.stream()
                        .max(Comparator.comparingInt(this::getStandardPointValue))
                        .orElseThrow();
                } else {
                    // Opponent is winning, must try to take the trick.
                    return playableCards.stream()
                        .filter(c -> compareKlaverjassenCards(c, highestCardInTrick) > 0)
                        .min(this::compareKlaverjassenCards) // Play lowest winning card
                        .orElseGet(() -> playableCards.stream()
                            .min(this::compareKlaverjassenCards)
                            .orElseThrow()); // Or else play lowest card to save points
                }
            }
        }

        // Rule 2: Cannot follow suit. Check for trumping obligations.
        final boolean hasTrump = hand.hasSuit(trumpSuit);
        final boolean trickContainsTrump = highestCardInTrick.getSuit() == trumpSuit;

        // Rotterdam variant special rule: If partner is winning, you don't have to trump.
        if (gameEngine.getGame().getGameVariant() == ROTTERDAMS && isPartnerWinning) {
            return discardCardWithSignal(hand.cards(), hand.bySuit());
        }

        // Standard trumping obligation applies.
        if (hasTrump) {
            final List<Card> trumpCards = hand.ofSuit(trumpSuit);
            final Optional<Card> overTrumpCard = trumpCards.stream()
                .filter(c -> compareKlaverjassenCards(c, highestCardInTrick) > 0)
                .min(this::compareKlaverjassenCards); // Find the lowest possible over-trump

            if (overTrumpCard.isPresent()) {
                return overTrumpCard.get(); // If you can over-trump, you MUST.
            }

            // Cannot over-trump. Check variant rules for under-trumping.
            if (trickContainsTrump) { // Only need to under-trump if a trump is already played
                if (gameEngine.getGame().getGameVariant() == ROTTERDAMS) {
                    // Rotterdam: If you can't over-trump, you can discard.
                    return discardCardWithSignal(hand.cards(), hand.bySuit());
                } else {
                    // Amsterdam: If you can't over-trump, you MUST under-trump.
                    return trumpCards.stream().min(this::compareKlaverjassenCards).orElseThrow();
                }
            } else {
                // You are the first to trump in this trick. You must play a trump.
                return trumpCards.stream().min(this::compareKlaverjassenCards).orElseThrow();
            }
        }

        // Rule 3: Cannot follow suit and cannot trump. Must discard.
        return discardCardWithSignal(hand.cards(), hand.bySuit());
    }

    /**
     * Discards a card, attempting to signal a strong suit to the partner.
     * A signal is made by discarding a low-value card from a suit where the AI holds an Ace or Ten.
     */
    private Card discardCardWithSignal(final List<Card> hand, final Map<Suit, List<Card>> cardsBySuit) {
        final Suit trumpSuit = gameEngine.getGame()
            .getTrump();

        // Strategy 1: Signal a strong suit.
        // Find a suit where we have an Ace or Ten, and discard a low card from it.
        List<Card> signalCards = cardsBySuit.entrySet()
            .stream()
            .filter(entry -> {
                // A suit is "strong" if we have a high card (Ace or Ten) in it.
                if (entry.getKey() == trumpSuit) {
                    return false;
                }
                boolean hasAce = entry.getValue()
                    .stream()
                    .anyMatch(c -> c.getRank() == Rank.ACE);
                boolean hasTen = entry.getValue()
                    .stream()
                    .anyMatch(c -> c.getRank() == Rank.TEN);
                return hasAce || hasTen;
            })
            .map(entry -> entry.getValue()
                .stream()
                .min(this::compareKlaverjassenCards)) // Get the lowest card of that strong suit
            .flatMap(Optional::stream)
            .sorted(this::compareKlaverjassenCards) // Sorts the signal cards from lowest to highest
            .toList();

        // Strategy 1: If we can signal, do it.
        // If we have multiple suits to signal, discard the lowest card of the second-best suit (the highest of the low cards).
        // This saves the signal for our best suit. If only one, discard from it.
        if (!signalCards.isEmpty()) {
            return signalCards.getLast();
        }

        // Strategy 2: No signal possible. Discard the lowest-value non-trump card.
        Optional<Card> lowestCardToDiscard = hand.stream()
            .filter(c -> c.getSuit() != trumpSuit)
            .min(this::compareKlaverjassenCards);

        // Strategy 3: Only trumps are left or no other option. Must discard the lowest card overall.
        return lowestCardToDiscard.orElseGet(() -> hand.stream()
            .min(this::compareKlaverjassenCards)
            .orElseThrow());
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
        return card.getSuit()
            .equals(gameEngine.getGame()
                .getTrump())
            ? card.getRank()
            .getTrumpValue()
            : card.getRank()
            .getStandardValue();
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
        final Suit trumpSuit = gameEngine.getGame()
            .getTrump();
        int handStrength = 0;

        final Map<Suit, List<Card>> cardsBySuit = hand.stream()
            .collect(Collectors.groupingBy(Card::getSuit));

        // 1. Evaluate trump suit strength
        final List<Card> trumpCards = cardsBySuit.getOrDefault(trumpSuit, List.of());
        final boolean hasTrumpJack = trumpCards.stream()
            .anyMatch(c -> c.getRank() == Rank.JACK);

        if (trumpCards.isEmpty()) {
            handStrength -= 20; // Massive penalty for being void in the trump suit.
        }

        final boolean hasTrumpNine = trumpCards.stream()
            .anyMatch(c -> c.getRank() == Rank.NINE);

        if (hasTrumpJack) {
            handStrength += 12; // The highest trump is a massive advantage.
        }

        if (hasTrumpNine) {
            handStrength += 8; // The second highest trump is also key.
        }


        // Bonus for having multiple high trumps
        if (hasTrumpJack && hasTrumpNine) {
            handStrength += 5;
        }

        // Bonus for length of the trump suit. Control of the game.
        if (trumpCards.size() >= 4) {
            handStrength += (trumpCards.size() - 3) * 5;
        }

        // Check for "Stuk" (King and Queen of trump)
        final boolean hasTrumpKing = trumpCards.stream()
            .anyMatch(c -> c.getRank() == Rank.KING);
        final boolean hasTrumpQueen = trumpCards.stream()
            .anyMatch(c -> c.getRank() == Rank.QUEEN);
        if (hasTrumpKing && hasTrumpQueen) {
            handStrength += 5; // This is worth 20 points if declared, but also indicates trump strength.
        }

        // Bonus for having a sequence (roem) in trump, indicates strong control.
        List<Integer> trumpRanks = trumpCards.stream()
            .map(c -> c.getRank()
                .getTrumpValue())
            .sorted()
            .collect(Collectors.toList());

        if (hasSequence(trumpRanks, 3)) {
            handStrength += 4;
        }
        if (hasSequence(trumpRanks, 4)) {
            handStrength += 6;
        }


        // 2. Evaluate non-trump suits for point-making potential
        for (Map.Entry<Suit, List<Card>> entry : cardsBySuit.entrySet()) {
            if (entry.getKey() == trumpSuit) {
                continue;
            }

            List<Card> suitCards = entry.getValue();
            boolean hasAce = suitCards.stream()
                .anyMatch(c -> c.getRank() == Rank.ACE);
            boolean hasTen = suitCards.stream()
                .anyMatch(c -> c.getRank() == Rank.TEN);
            boolean hasKing = suitCards.stream()
                .anyMatch(c -> c.getRank() == Rank.KING);
            boolean hasQueen = suitCards.stream()
                .anyMatch(c -> c.getRank() == Rank.QUEEN);

            if (hasAce) {
                handStrength += 7; // A non-trump Ace is often a guaranteed trick.
            }

            if (hasTen) {
                handStrength += 4; // A non-trump Ten is a likely point winner
            }

            // Add points for "defensive" honors that are not naked.
            if (hasKing && suitCards.size() > 1) {
                handStrength += 2;
            }
            if (hasQueen && suitCards.size() > 1) {
                handStrength += 1;
            }
            // Penalty for "naked" honors.
            if (hasAce && suitCards.size() == 1) {
                handStrength += 2; // A naked Ace is still good, just not as good as a supported one. Reverts penalty.
            }
            if (hasTen && suitCards.size() == 1) {
                handStrength -= 2;
            }
            if (hasKing && suitCards.size() == 1) {
                handStrength -= 2; // A naked King is a liability.
            }
            if (hasQueen && suitCards.size() == 1) {
                handStrength -= 1; // A naked Queen is a small liability.
            }
            if (hasAce && hasTen) {
                handStrength += 3;  // Ace-Ten combo is very strong.
            }
        }

        // 3. Evaluate distribution for voiding/trumping potential
        for (Suit s : Suit.values()) {
            if (s == trumpSuit) {
                continue;
            }
            int suitSize = cardsBySuit.getOrDefault(s, List.of())
                .size();
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

    private boolean hasSequence(final List<Integer> sortedRanks, final int length) {
        if (sortedRanks.size() < length) {
            return false;
        }
        for (int i = 0; i <= sortedRanks.size() - length; i++) {
            if (sortedRanks.get(i + length - 1) - sortedRanks.get(i) == length - 1) {
                return true;
            }
        }
        return false;
    }
}
