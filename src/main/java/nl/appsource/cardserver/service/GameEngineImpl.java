package nl.appsource.cardserver.service;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.Rank;
import nl.appsource.cardserver.model.Suit;
import nl.appsource.cardserver.service.exception.CardAlreadyPlayerException;
import nl.appsource.cardserver.service.exception.ElderException;
import nl.appsource.cardserver.service.exception.GameCompletedException;
import nl.appsource.cardserver.service.exception.GameEngineException;
import nl.appsource.cardserver.service.exception.LastTrickOpenException;
import nl.appsource.cardserver.service.exception.NotAPlayerException;
import nl.appsource.cardserver.service.exception.NotPlayersTurnException;
import org.openapitools.model.GameVariant;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static nl.appsource.cardserver.service.GameServiceImpl.randomCards;

@Slf4j
public record GameEngineImpl(Game game) implements GameEngine {

    public static final List<String> AI_USER_ID = List.of("2ab5fd69a2796c4740380cd98eb7", "2ab5fd69a2796c4740380cd98eb8", "2ab5fd69a2796c4740380cd98eb9");

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

    @Override
    public Card determineTrickWinningCard(final List<Card> trick) {

        if (trick.isEmpty()) {
            throw new RuntimeException("Empty trick");
        }

        final boolean troefAanwezig = trick.stream()
            .anyMatch(c -> c.getSuit()
                .equals(game.getTrump()));

        final Suit requestedSuit = trick.getFirst()
            .getSuit();

        return trick.stream()
            .filter(c -> c.getSuit()
                .equals(troefAanwezig ? game.getTrump() : requestedSuit))
            .max(troefAanwezig ? TRUMP_SORTER : REGULAR_SORTER)
            .orElseThrow(() -> new RuntimeException("determineTrickWinningCard() No card found"));

    }

    @Override
    public int determineTrickWinningPlayer(final int trickNr) {

        if (trickNr >= calcTricksPlayed() || trickNr < 0) {
            throw new RuntimeException("no such trick " + trickNr);
        }

        final List<Card> trick = getTrickCards(trickNr);
        final Card winningCard = determineTrickWinningCard(trick);
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
    public Mono<GameEngine> playAiCard() {

        if (!isAiTurn()) {
            return Mono.empty();
        }

        final String userId = game.getPlayers()
            .get(calcWhoHasTurn());

        if (!AI_USER_ID.contains(userId)) {
            throw new GameEngineException("Not an Ai player");
        }

        return playCard(userId, new AiPlayer(this).calcAiCard(userId));

    }

    @Override
    public Mono<GameEngine> sayAi() {

        if (!isAiSay()) {
            return Mono.empty();
        }

        final int whoSay = calcWhoSay();

        final String userId = this.game.getPlayers()
            .get(whoSay);

        return say(userId, new AiPlayer(this).decideBid(userId));

    }

    @Override
    public Mono<GameEngine> playCard(final String userId, final Card card) {

        final int playerNum = this.game.getPlayers()
            .indexOf(userId);

        if (isCompleted()) {
            log.warn("Game {} allready completed", game.getId());
            throw new GameCompletedException();
        }

        if (!game.getPlayers()
            .contains(userId)) {
            throw new RuntimeException("Not a player");
        }

        final int cardOwner = whoHasCard(card);

        if (playerNum != cardOwner) {
            log.warn("Player does not have card {}", card);
        }

        if (game.getTurns()
            .stream()
            .anyMatch((c) -> c == card)) {
            log.warn("Card {} allready played", card);
            throw new CardAlreadyPlayerException(card);
        }

        if (game.getLastTrickOpen()) {
            return Mono.empty();
        }

        final int gotTurn = calcWhoHasTurn();

        if (gotTurn != playerNum) {
            log.warn("playCard({}) It's player {} turn", card, game.getPlayers()
                .get(gotTurn));
            throw new NotPlayersTurnException();
        }

        log.info("playCard() game: {}, card: {}, player: {}", game.getId(), card, userId);

        game.setUpdated(Instant.now());
        game.getTurns()
            .add(card);
        game.setLastTrickOpen(false);

        return Mono.just(this);

    }

    @Override
    public Mono<GameEngine> say(final String userId, final Boolean say) {

//        final List<UserMessage> userMessages = new ArrayList<>();

        final int playerNum = this.game.getPlayers()
            .indexOf(userId);

        if (isCompleted()) {
            log.warn("Game {} allready completed", game.getId());
            throw new GameCompletedException();
        }

        if (!game.getPlayers()
            .contains(userId)) {
            throw new NotAPlayerException();
        }

        if (game.getSay() == null) {
            game.setSay(new HashMap<>());
        }

        if (isErGegaan()) {
            throw new ElderException("Er is al iemand gegaan");
        }

        if (game.getSay()
            .containsKey(playerNum)) {
            throw new ElderException("Je hebt al gezegd");
        }

        if (game.getLastTrickOpen()) {
            throw new LastTrickOpenException();
        }

        final int whoSay = calcWhoSay();

        if (whoSay != playerNum) {
            log.warn("say() It's player {} turn to say", game.getPlayers()
                .get(whoSay));
            throw new NotPlayersTurnException();
        }

        game.getSay()
            .put(playerNum, say);

        // userMessages.add(new UserMessage().message(userId + " " + (say ? "gaat!" : "past")).variant(say ? UserMessage.VariantEnum.SUCCESS : UserMessage.VariantEnum.INFO));

        checkNiemandIsGegaanEnIedereenHeeftGezegd();

        game.setUpdated(Instant.now());

//        return userMessages;

        return Mono.just(this);

    }

    @Override
    public Mono<GameEngine> checkNiemandIsGegaanEnIedereenHeeftGezegd() {
        if (niemandIsGegaanEnIedereenHeeftGezegd()) {
            if (game.getDealCounter() % 2 == 0) {

                final Suit oldTrump = this.game.getTrump();

                do {
                    game.setTrump(Suit.values()[RAND.nextInt(Suit.values().length)]);
                }
                while (oldTrump == game.getTrump());
            } else {
                game.setTrump(Suit.values()[RAND.nextInt(Suit.values().length)]);
                game.setPlayerCard(randomCards());
            }

            game.getSay()
                .clear();

            game.setDealCounter(game.getDealCounter() + 1);
            game.setUpdated(Instant.now());

            return Mono.just(this);
        } else {
            return Mono.empty();
        }

    }

    @Override
    public boolean isCompleted() {
        return getTurnCount() >= 32;
    }

    private int whoHasCard(final Card card) {
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
    public Mono<GameEngine> openLastTrick() {
        if (!isCompleted() && getTurnCount() > 4) {
            if (!this.getGame()
                .getLastTrickOpen()) {
                this.getGame()
                    .setLastTrickOpen(true);
                return Mono.just(this);
            }
        }
        return Mono.empty();
    }

    @Override
    public Mono<GameEngine> closeLastTrick() {
        if (this.getGame()
            .getLastTrickOpen()) {
            this.getGame()
                .setLastTrickOpen(false);
            return Mono.just(this);
        } else {
            return Mono.empty();
        }
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
            .get(whoHasCard(determineTrickWinningCard(currentTrick)));
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

    private boolean mustTrump(final List<Card> hand, final List<Card> currentTrick) {
        if (currentTrick.isEmpty()) {
            return false;
        }
        final Card leadingCard = currentTrick.getFirst();
        final Suit leadingSuit = leadingCard.getSuit();

        // If player can follow suit, they are not forced to trump.
        final boolean canFollowSuit = hand.stream()
            .anyMatch(c -> c.getSuit()
                .equals(leadingSuit));
        if (canFollowSuit) {
            return false;
        }

        // Player cannot follow suit. They must trump if they have a trump card.
        return hand.stream()
            .anyMatch(c -> c.getSuit()
                .equals(game.getTrump()));
    }

    @Override
    public Boolean verzaakt(final int correctedSlagNr, final int speler) {

        final List<Card> trick = getTrickCards(correctedSlagNr);

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

        // Check for not following suit
        final boolean couldFollowSuit = handAtTimeOfPlay.stream()
            .anyMatch(c -> c.getSuit()
                .equals(leadingSuit));
        if (couldFollowSuit && playedCard.getSuit() != leadingSuit) {
            return true; // Verzaakt: Did not follow suit when possible
        }

        // Check for not trumping when required
        if (!couldFollowSuit) {
            final List<Card> trickBeforePlay = trick.subList(0, playerTurnInTrick);
            if (mustTrump(handAtTimeOfPlay, trickBeforePlay) && playedCard.getSuit() != game.getTrump()) {
                return true; // Verzaakt: Did not trump when required
            }

            // Rotterdam variant: check for over-trumping
            if (game.getGameVariant() == GameVariant.ROTTERDAMS && playedCard.getSuit() == game.getTrump()) {
                final Card highestTrumpOnTable = trickBeforePlay.stream()
                    .filter(c -> c.getSuit() == game.getTrump())
                    .max(TRUMP_SORTER)
                    .orElse(null);

                if (highestTrumpOnTable != null) {
                    final boolean couldOverTrump = handAtTimeOfPlay.stream()
                        .filter(c -> c.getSuit() == game.getTrump())
                        .anyMatch(c -> TRUMP_SORTER.compare(c, highestTrumpOnTable) > 0);

                    return couldOverTrump && TRUMP_SORTER.compare(playedCard, highestTrumpOnTable) < 0; // Verzaakt: Did not over-trump when possible
                }
            }
        }

        return false;
    }
}
