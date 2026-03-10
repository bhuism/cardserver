package nl.appsource.cardserver.couchbase.utils;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.Rank;
import nl.appsource.cardserver.model.Suit;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static nl.appsource.cardserver.model.GameVariant.ROTTERDAMS;

@Slf4j
public record GameEngineImpl(Game game) implements GameEngine {

    public static final Set<String> AI_USER_ID = Set.of("2ab5fd69a2796c4740380cd98eb7", "2ab5fd69a2796c4740380cd98eb8", "2ab5fd69a2796c4740380cd98eb9", "2ab5fd69a2796c4740380cd98eba");

    private static final Comparator<? super Card> TRUMP_SORTER = comparing((Card o) -> o.rank.trumpValue).thenComparing(o -> -o.rank.ordinal());

    private static final Comparator<? super Card> REGULAR_SORTER = comparing((Card o) -> o.rank.standardValue).thenComparing(o -> -o.rank.ordinal());

    private static final Random RAND = new SecureRandom();

    @Override
    public int calcTricksPlayed() {
        return getTurnCount() / 4;
    }

    @Override
    public boolean isFullTrick() {
        return getTurnCount() % 4 == 0 && this.getTurnCount() >= 4;
    }

    @Override
    public List<Card> getTrickCards(final int trickNr) {
        if (trickNr < 0 || trickNr > 7) {
            throw new RuntimeException("Invalid trick nr " + trickNr);
        }
        return game.getTurns()
            .subList(trickNr * 4, Math.min(getTurnCount(), trickNr * 4 + 4));
    }

    public static Card determineTrickWinningCard(final List<Card> trick, final Suit trump) {

        if (trick.isEmpty()) {
            throw new RuntimeException("Empty trick");
        }

        final boolean troefAanwezig = trick.stream()
            .anyMatch(c -> c.getSuit()
                .equals(trump));

        final Suit requestedSuit = trick.getFirst()
            .getSuit();

        return trick.stream()
            .filter(c -> c.getSuit()
                .equals(troefAanwezig ? trump : requestedSuit))
            .max(troefAanwezig ? TRUMP_SORTER : REGULAR_SORTER)
            .orElseThrow(() -> new RuntimeException("determineTrickWinningCard() No card found"));

    }

    @Override
    public int determineTrickWinningPlayer(final int trickNr) {

        if (trickNr >= calcTricksPlayed() || trickNr < 0) {
            throw new RuntimeException("no such trick " + trickNr);
        }

        final List<Card> trick = getTrickCards(trickNr);
        final Card winningCard = determineTrickWinningCard(trick, this.game.getTrump());
        return whoHasCard(winningCard);
    }

    public int calcWhoSay() {

        if (isCompleted()) {
            throw new RuntimeException();
        }

        if (game.getSay() == null) {
            return (game.getDealer() + 1) % 4;
        }

        if (isErGegaan()) {
            throw new RuntimeException("Er is al gegaan");
        }

        if (game.getSay()
            .size() <= 3) {
            return (game.getDealer() + 1 + game.getSay()
                .size()) % 4;
        }

        throw new RuntimeException("Draaien");

    }

    @Override
    public int calcWhoHasTurn() {

        if (isCompleted()) {
            throw new RuntimeException();
        }

        if (game.getSay() == null || !isErGegaan()) {
            throw new RuntimeException("Nobody went");
        }

        final int laatsteKaart = getTurnCount() % 4;

        if (laatsteKaart != 0) {
            return (whoHasCard(game.getTurns()
                .getLast()) + 1) % 4;
        } else {
            if (game.getTurns()
                .isEmpty()) {
                return (game.getDealer() + 1) % 4;
            } else {
                final int tricksPlayedCount = calcTricksPlayed();
                return determineTrickWinningPlayer(tricksPlayedCount - 1) + getTurnCount() % 4;
            }
        }
    }

    @Override
    public boolean isCompleted() {
        return getTurnCount() >= 32;
    }

    @Override
    public int whoHasCard(final Card card) {
        return game.getPlayerCard()
            .get(card);
    }

    @Override
    public boolean isAiTurn() {

        if (isCompleted()) {
            return false;
        }

        if (game.getSay() == null || !isErGegaan()) {
            return false;
        }

        if (getGame().getLastTrickOpen()) {
            return false;
        }

        return isAiPlayer(game.getPlayers()
            .get(calcWhoHasTurn()));

    }

    @Override
    public boolean isAiSay() {

        if (isCompleted()) {
            return false;
        }

        if (game.getSay() == null) {
            game.setSay(new HashMap<>());
        }

        if (isErGegaan()) {
            return false;
        }

        if (iedereenHeeftGezegd()) {
            return false;
        }

        if (getGame().getLastTrickOpen()) {
            return false;
        }

        return isAiPlayer(game.getPlayers()
            .get(calcWhoSay()));

    }

    @Override
    public Game getGame() {
        return game;
    }

    public static boolean isAiPlayer(final String userId) {
        return AI_USER_ID.contains(userId);
    }

    @Override
    public boolean isErGegaan() {
        return game.getSay()
            .containsValue(Boolean.TRUE);
    }

    @Override
    public boolean niemandIsGegaanEnIedereenHeeftGezegd() {
        return iedereenHeeftGezegd() && !game.getSay()
            .containsValue(Boolean.TRUE);
    }

    @Override
    public boolean iedereenHeeftGezegd() {
        return game.getSay()
            .size() == 4;
    }

    @Override
    public int getTurnCount() {
        return game.getTurns()
            .size();
    }

    @Override
    public boolean isLastTrick() {
        return getTurnCount() >= 7 * 4;
    }

    @Override
    public String getPartner(final String userId) {
        final int index = game.getPlayers()
            .indexOf(userId);

        final int partnerIndex = (index + 2) % 4;

        return game.getPlayers()
            .get(partnerIndex);

    }

    @Override
    public String getTrickWinnerId(final List<Card> currentTrick) {
        return this.getGame()
            .getPlayers()
            .get(whoHasCard(determineTrickWinningCard(currentTrick, this.game.getTrump())));
    }

    @Override
    public int calculateTrickPoints(final int trickNr) {
        return
            this.getTrickCards(trickNr)
                .stream()
                .map((c) -> (c.suit == this.game.getTrump() ? c.rank.trumpValue : c.rank.standardValue))
                .reduce(0, Integer::sum) + (trickNr == 7 ? 10 : 0);

    }

    @SuppressWarnings("InnerAssignment")
    @Override
    public int calculateTrickRoem(final int trickNr) {
        final List<Card> trick = this.getTrickCards(trickNr);

        int roem = 0;

        // Check for four of a kind
        final Map<Rank, Long> countsByRank = trick.stream()
            .collect(Collectors.groupingBy(Card::getRank, Collectors.counting()));

        for (Map.Entry<Rank, Long> entry : countsByRank.entrySet()) {
            if (entry.getValue() == 4) {
                switch (entry.getKey()) {
                    case ACE, KING, QUEEN -> roem += 100;
                    case JACK -> roem += 200;
                    default -> {
                    }
                }
            }
        }

        // Check for sequences
        final Map<Suit, List<Card>> cardsBySuit = trick.stream()
            .collect(Collectors.groupingBy(Card::getSuit));

        for (List<Card> suitCards : cardsBySuit.values()) {
            if (suitCards.size() < 3) {
                continue;
            }

            suitCards.sort(Comparator.comparing(card -> card.getRank()
                .ordinal()));

            boolean isFourInARow = false;
            if (suitCards.size() == 4) {
                if (suitCards.get(0)
                    .getRank()
                    .ordinal() + 1 == suitCards.get(1)
                    .getRank()
                    .ordinal()
                    && suitCards.get(1)
                    .getRank()
                    .ordinal() + 1 == suitCards.get(2)
                    .getRank()
                    .ordinal()
                    && suitCards.get(2)
                    .getRank()
                    .ordinal() + 1 == suitCards.get(3)
                    .getRank()
                    .ordinal()) {
                    roem += 50;
                    isFourInARow = true;
                }
            }

            if (!isFourInARow && suitCards.size() >= 3) {
                for (int i = 0; i <= suitCards.size() - 3; i++) {
                    if (suitCards.get(i)
                        .getRank()
                        .ordinal() + 1 == suitCards.get(i + 1)
                        .getRank()
                        .ordinal()
                        && suitCards.get(i + 1)
                        .getRank()
                        .ordinal() + 1 == suitCards.get(i + 2)
                        .getRank()
                        .ordinal()) {
                        roem += 20;
                        break;
                    }
                }
            }
        }

        // Check for Stuk (King and Queen of trump)
        final Suit trumpSuit = game.getTrump();
        if (trick.stream()
            .anyMatch(c -> c.getSuit() == trumpSuit && c.getRank() == Rank.KING)
            && trick.stream()
            .anyMatch(c -> c.getSuit() == trumpSuit && c.getRank() == Rank.QUEEN)) {
            roem += 20;
        }

        return roem;
    }

    @Override
    public Boolean getErIsGegaan() {
        return this.game.getSay()
            .containsValue(true);
    }


    @Override
    public List<Card> getHuidigeTableCards() {
        return getTrickCards(calcTricksPlayed() == 0 ? 0 : calcTricksPlayed() - (getTurnCount() % 4 == 0 ? 1 : 0));
    }

    private List<Card> getHand(final String userId) {

        final int playerNum = game
            .getPlayers()
            .indexOf(userId);

        return game
            .getPlayerCard()
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue()
                .equals(playerNum))
            .map(Map.Entry::getKey)
            .filter(card -> !game
                .getTurns()
                .contains(card))
            .collect(Collectors.toList());
    }

    @Override
    public Boolean verzaakt(final int slagNr, final int speler) {

        final List<Card> trick = getTrickCards(slagNr);

        final Card playedCard = trick.stream()
            .filter(c -> whoHasCard(c) == speler)
            .findFirst()
            .orElse(null);

        if (playedCard == null) {
            // Player did not play in this trick, so cannot have reneged in it.
            return false;
        }

        final int playerTurnInTrick = trick.indexOf(playedCard);

        // The leader of the trick cannot renege
        if (playerTurnInTrick == 0) {
            return false;
        }

        final Card leadingCard = trick.getFirst();
        final Suit leadingSuit = leadingCard.getSuit();

        // Reconstruct hand at time of play
        final List<Card> initialHand = game.getPlayerCard()
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue().equals(speler))
            .map(Map.Entry::getKey)
            .toList();

        final int turnIndexOfPlayedCard = game.getTurns().indexOf(playedCard);

        final List<Card> playedByPlayerBefore = game.getTurns()
            .subList(0, turnIndexOfPlayedCard)
            .stream()
            .filter(c -> whoHasCard(c) == speler)
            .toList();

        final List<Card> handAtTimeOfPlay = new ArrayList<>(initialHand);

        handAtTimeOfPlay.removeAll(playedByPlayerBefore);

        // Check for not following suit (Volgplicht)
        final boolean couldFollowSuit = handAtTimeOfPlay.stream()
            .anyMatch(c -> c.getSuit().equals(leadingSuit));
        if (couldFollowSuit && playedCard.getSuit() != leadingSuit) {
            return true; // Verzaakt: Did not follow suit when possible.
        }

        final Suit trumpSuit = game.getTrump();
        final List<Card> trickBeforePlay = trick.subList(0, playerTurnInTrick);

        // Determine who is winning the trick so far
        Integer currentWinner = null;
        if (!trickBeforePlay.isEmpty()) {
            final Card winningCard = determineTrickWinningCard(trickBeforePlay, trumpSuit);
            currentWinner = whoHasCard(winningCard);
        }

        final boolean partnerIsWinning = currentWinner != null && isPartner(speler, currentWinner);
        final boolean isRotterdams = game.getGameVariant() == ROTTERDAMS;

        // Check for trumping rules (Troefplicht)
        if (!couldFollowSuit) {
            final boolean hasTrump = handAtTimeOfPlay.stream().anyMatch(c -> c.getSuit() == trumpSuit);

            boolean mustTrump = false;
            if (isRotterdams) {
                mustTrump = true; // In Rotterdam, always must trump if cannot follow suit
            } else {
                // Amsterdam: Must trump only if opponent is winning
                if (!partnerIsWinning) {
                    mustTrump = true;
                }
            }

            if (mustTrump && hasTrump && playedCard.getSuit() != trumpSuit) {
                return true; // Verzaakt: Had to trump but didn't.
            }
        }

        // Check for over-trumping (Over-troefplicht)
        if (playedCard.getSuit() == trumpSuit) {
            final Card highestTrumpOnTable = trickBeforePlay.stream()
                .filter(c -> c.getSuit() == trumpSuit)
                .max(TRUMP_SORTER)
                .orElse(null);

            if (highestTrumpOnTable != null) {
                final boolean partnerHasHighestTrump = currentWinner != null && isPartner(speler, currentWinner) && whoHasCard(highestTrumpOnTable) == currentWinner;

                // In Amsterdam, you don't have to over-trump your partner.
                // In Rotterdam, you DO have to over-trump your partner.
                boolean mustOverTrump = true;
                if (!isRotterdams && partnerHasHighestTrump) {
                    mustOverTrump = false;
                }

                if (mustOverTrump) {
                    final boolean couldOverTrump = handAtTimeOfPlay.stream()
                        .filter(c -> c.getSuit() == trumpSuit)
                        .anyMatch(c -> TRUMP_SORTER.compare(c, highestTrumpOnTable) > 0);

                    if (couldOverTrump && TRUMP_SORTER.compare(playedCard, highestTrumpOnTable) < 0) {
                        return true; // Verzaakt: Did not over-trump when possible.
                    }
                }
            }
        }

        return false;
    }

    private boolean isPartner(final int p1, final int p2) {
        return (p1 + 2) % 4 == p2;
    }

}
