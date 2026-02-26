package nl.appsource.cardserver.service;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Rank;
import nl.appsource.cardserver.model.Suit;
import nl.appsource.cardserver.service.exception.GameEngineException;
import nl.appsource.generated.openapi.model.AiRisc;
import nl.appsource.generated.openapi.model.GameVariant;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
            return playAsLeader(userId, hand);
        } else {
            return playAsFollower(userId, hand, currentTrick, getHighestCardInTrick(currentTrick));
        }
    }

    /**
     * Logic for when the AI is the first to play in a trick.
     */
    private Card playAsLeader(final String userId, final Hand hand) {

        log.trace("playAsLeader for user: {} with hand: {}", userId, hand.cards);

        // New Strategy: If partner is "gegaan", lead with a trump to support them.
        final String partnerId = gameEngine.getPartner(userId);
        final int partnerIndex = gameEngine.getGame().getPlayers().indexOf(partnerId);
        final Boolean partnerSaidGo = gameEngine.getGame().getSay().getOrDefault(partnerIndex, false);

        final Suit trumpSuit = gameEngine.getGame().getTrump();
        final List<Card> trumpCards = hand.ofSuit(trumpSuit);

        if (Boolean.TRUE.equals(partnerSaidGo) && !trumpCards.isEmpty()) {
            log.trace("Partner ({}) is 'gegaan', leading with highest trump.", partnerId);
            return trumpCards.stream()
                .max(this::compareKlaverjassenCards)
                .orElseThrow(); // Safe, as trumpCards is not empty
        }

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
                .thenComparing(this::getKlaverjassenRank)); // Then play lowest card of that suit

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

        log.trace("playAsFollower {} {} {} ", hand.cards, currentTrick, highestCardInTrick);

        final Suit leadingSuit = currentTrick.getFirst().getSuit();
        final Suit trumpSuit = gameEngine.getGame().getTrump();
        final String partnerId = gameEngine.getPartner(userId);
        final String currentWinnerId = gameEngine.getTrickWinnerId(currentTrick);
        final boolean isPartnerWinning = currentWinnerId.equals(partnerId);

        log.trace("playAsFollower {} {} {} {} {} ", leadingSuit, trumpSuit, partnerId, currentWinnerId, isPartnerWinning);

        // Rule 1: Must follow suit if possible.
        if (hand.hasSuit(leadingSuit)) {

            log.trace("playAsFollower hand.hasSuit(leadingSuit)");

            final List<Card> playableCards = hand.ofSuit(leadingSuit);
            // If leading suit is trump, you must try to play a higher trump if you can.
            if (leadingSuit == trumpSuit) {
                log.trace("playAsFollower leadingSuit == trumpSuit");
                return playableCards.stream()
                    .filter(c -> compareKlaverjassenCards(c, highestCardInTrick) > 0)
                    .min(this::compareKlaverjassenCards) // Play lowest winning card
                    .orElseGet(() -> playableCards.stream()
                        .min(this::compareKlaverjassenCards)
                        .orElseThrow()); // Or else play lowest card of the suit
            } else {

                log.trace("playAsFollower leadingSuit != trumpSuit");

                // Not a trump suit.
                if (isPartnerWinning) {

                    log.trace("playAsFollower isPartnerWinning");

                    // Partner is winning, so "smear" with high-value cards.
                    return playableCards.stream()
                        .max(Comparator.comparingInt(this::getStandardPointValue))
                        .orElseThrow();
                } else {


                    log.trace("playAsFollower !isPartnerWinning");

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

        // Rule 2: Cannot follow suit. You must play a trump if you have one.
        if (hand.hasSuit(trumpSuit)) {
            log.trace("playAsFollower hasTrump");

            final List<Card> trumpCards = hand.ofSuit(trumpSuit);
            final boolean trickContainsTrump = highestCardInTrick.getSuit() == trumpSuit;

            // Find the lowest trump card that can beat the current highest card.
            final Optional<Card> overTrumpCard = trumpCards.stream()
                .filter(c -> compareKlaverjassenCards(c, highestCardInTrick) > 0)
                .min(this::compareKlaverjassenCards);

            // If you can over-trump...
            if (overTrumpCard.isPresent()) {

                if (GameVariant.ROTTERDAMS.equals(gameEngine.getGame().getGameVariant())) {
                    // In Rotterdams, if you can over-trump, you MUST over-trump, regardless of partner winning.
                    return overTrumpCard.get();
                }

                log.trace("overTrumpCard.isPresent()");

                // ...you must do so unless your partner is already winning.
                if (isPartnerWinning) {
                    // Partner is winning, no need to waste a high trump. Play the lowest trump.
                    return trumpCards.stream().min(this::compareKlaverjassenCards).orElseThrow();
                } else {
                    // An opponent is winning. You must over-trump. Play the lowest possible over-trump card.
                    return overTrumpCard.get();
                }
            }

            // You CANNOT over-trump. Now the rules for under-trumping apply.
            // This situation only matters if an opponent is already winning with a trump.
            if (trickContainsTrump && !isPartnerWinning) {

                log.trace("trickContainsTrump && !isPartnerWinning");

                // Amsterdam/Rotterdam Rule: If you cannot over-trump, you MUST under-trump.
                return trumpCards.stream().min(this::compareKlaverjassenCards).orElseThrow();
            } else {
                // This covers cases where:
                // a) No trump has been played yet.
                // b) Your partner is winning (with a trump or non-trump).
                // In these cases, you still must play a trump (since you couldn't follow suit), so play your lowest one.
                return trumpCards.stream().min(this::compareKlaverjassenCards).orElseThrow();
            }
        }

        // Rule 3: Cannot follow suit and have no trump. Must discard.
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
     * Compares cards based on their game-winning rank (trump > non-trump).
     */
    private int compareKlaverjassenCards(final Card c1, final Card c2) {
        return Integer.compare(getKlaverjassenRank(c1), getKlaverjassenRank(c2));
    }

    /**
     * Gets the Klaverjassen trick-taking rank of a card.
     * Trump: J > 9 > A > 10 > K > Q > 8 > 7
     * Non-Trump: A > 10 > K > Q > J > 9 > 8 > 7
     * A higher value means a higher rank. A trump card is always higher than a non-trump.
     */
    private int getKlaverjassenRank(final Card card) {
        final boolean isTrump = card.getSuit().equals(gameEngine.getGame().getTrump());
        if (isTrump) {
            return switch (card.getRank()) {
                case JACK -> 22;
                case NINE -> 21;
                case ACE -> 20;
                case TEN -> 19;
                case KING -> 18;
                case QUEEN -> 17;
                case EIGHT -> 16;
                case SEVEN -> 15;
            };
        } else {
            return switch (card.getRank()) {
                case ACE -> 14;
                case TEN -> 13;
                case KING -> 12;
                case QUEEN -> 11;
                case JACK -> 10;
                case NINE -> 9;
                case EIGHT -> 8;
                case SEVEN -> 7;
            };
        }
    }


    /**
     * Gets the Klaverjassen point-value of a card (e.g., Trump Jack is 20).
     */
    private int getKlaverjassenPoints(final Card card) {
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

    static final Map<AiRisc, Integer> AI_RISC_BIDDING_THRESHOLD_MAP = Map.of(
        AiRisc.VERYLOW, 44,
        AiRisc.LOW, 40,
        AiRisc.MEDIUM, 37,
        AiRisc.HIGH, 35,
        AiRisc.VERYHIGH, 31
    );

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
        if (hasSequence(trumpCards, 3)) {
            handStrength += 4;
        }
        if (hasSequence(trumpCards, 4)) {
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

        final boolean decision = handStrength >= AI_RISC_BIDDING_THRESHOLD_MAP.get(gameEngine.getGame().getAiRisc());
        log.trace("{}: evaluates their hand for trump: {}, with strength: {}, decision={}", userId, trumpSuit, handStrength, decision);
        return decision;
    }

    private boolean hasSequence(final List<Card> cards, final int length) {
        if (cards.size() < length) {
            return false;
        }
        // Get ranks, sort them descending, and remove duplicates
        final List<Integer> sortedRanks = cards.stream()
            .map(this::getKlaverjassenRank)
            .distinct()
            .sorted(Comparator.reverseOrder())
            .toList();

        if (sortedRanks.size() < length) {
            return false;
        }

        // Check for a consecutive sequence of ranks
        for (int i = 0; i <= sortedRanks.size() - length; i++) {
            if (sortedRanks.get(i) - sortedRanks.get(i + length - 1) == length - 1) {
                return true;
            }
        }
        return false;
    }
}
