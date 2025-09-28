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
import nl.appsource.cardserver.service.exception.NeedNewSayRound;
import nl.appsource.cardserver.service.exception.NotAPlayerException;
import nl.appsource.cardserver.service.exception.NotPlayersTurnException;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.Instant;
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

    public boolean isFirstTrickCard() {
        return getTurnCount() % 4 == 0;
    }

    @Override
    public List<Card> getTrickCards(final int trickNr) {
        return game.getTurns()
            .subList(trickNr * 4, Math.min(getTurnCount(), trickNr * 4 + 4));
    }

    private Card determineTrickWinningCard(final List<Card> trick) {

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
    public int determineTrickWinner(final int trickNr) {

        if (trickNr >= calcTricksPlayed() || trickNr < 0) {
            throw new RuntimeException("no such trick " + trickNr);
        }

        final List<Card> trick = getTrickCards(trickNr);

        final Card winningCard = determineTrickWinningCard(trick);
        return whoHasCard(winningCard);
    }

    public int calcWhoSay() throws GameEngineException {

        if (isCompleted()) {
            throw new GameCompletedException();
        }

        if (game.getSay() == null) {
            return (game.getDealer() + 1) % 4;
        }

        if (isErGegaan()) {
            throw new ElderException(null);
        }

        if (game.getSay()
            .size() <= 3) {
            return (game.getDealer() + 1 + game.getSay()
                .size()) % 4;
        }

        throw new NeedNewSayRound(null);

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
            return (whoHasCard(game.getTurns().getLast()) + 1) % 4;
        } else {
            if (game.getTurns()
                .isEmpty()) {
                return (game.getDealer() + 1) % 4;
            } else {
                final int tricksPlayedCount = calcTricksPlayed();
                return determineTrickWinner(tricksPlayedCount - 1) + getTurnCount() % 4;
            }
        }
    }

    @Override
    public Mono<GameEngine> playAiCard() throws GameEngineException {

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
    public Mono<GameEngine> sayAi() throws GameEngineException {

        if (!isAiSay()) {
            return Mono.empty();
        }

        final int whoSay = calcWhoSay();

        final String userId = this.game.getPlayers()
            .get(whoSay);

        return say(userId, new AiPlayer(this).decideBid(userId));

    }

    @Override
    public Mono<GameEngine> playCard(final String userId, final Card card) throws GameEngineException {

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

//        final List<Card> currentTrick = getTrickCards(calcTricksPlayed());

//        if (!currentTrick.isEmpty()) {
//            final Card leadingCard = currentTrick.getFirst();
//            final List<Card> hand = getHand(userId);
//            final boolean canFollowSuit = hand.stream().anyMatch(c -> c.getSuit().equals(leadingCard.getSuit()));

        // Check for reneging (verzaak)
//            if (canFollowSuit && card.getSuit() != leadingCard.getSuit()) {
//                userMessages.add(new UserMessage().userId(userId).message("Verzaakt! U moet kleur bekennen.").variant(UserMessage.VariantEnum.WARNING));
//                // In a real game, this might be a penalty. For now, we just warn.
//            } else if (!canFollowSuit && card.getSuit() != game.getTrump() && mustTrump(hand, currentTrick)) {
//                // Check for failure to trump when required
//                final String message = game.getGameVariant() == GameVariant.AMSTERDAMS
//                    ? "Verzaakt! U moet introeven (Amsterdams)." : "Verzaakt! U moet overtroeven (Rotterdams).";
//                userMessages.add(new UserMessage().userId(userId).message(message).variant(UserMessage.VariantEnum.WARNING));
//            }
//        }

        log.info("playCard() game: {}, card: {}, player: {}", game.getId(), card, userId);

        game.setUpdated(Instant.now());
        game.getTurns()
            .add(card);
        game.setLastTrickOpen(false);

        return Mono.just(this);
//        return userMessages;

    }

//    private boolean mustTrump(final List<Card> hand, final List<Card> currentTrick) {
//        final Card leadingCard = currentTrick.getFirst();
//        final Suit leadingSuit = leadingCard.getSuit();
//
//        if (leadingSuit == game.getTrump() || !hasSuit(hand, game.getTrump())) {
//            return false;
//        }
//
//        if (game.getGameVariant() == GameVariant.AMSTERDAMS) {
//            return true; // Amsterdam: always trump if you can't follow suit
//        } else { // Rotterdams (and others as default)
//            final Card highestCardInTrick = getHighestCardInTrick(currentTrick);
//            if (highestCardInTrick.getSuit() != game.getTrump()) {
//                return true; // If no trump is on the table, you must trump.
//            }
//            // You only have to trump if you can play a higher trump.
//            return hand.stream().anyMatch(c -> c.getSuit() == game.getTrump() && compareKlaverjassenCards(c, highestCardInTrick) > 0);
//        }
//    }

    @Override
    public Mono<GameEngine> say(final String userId, final Boolean say) throws GameEngineException {

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

        if (niemandIsGegaanEnIedereenHeeftGezegd()) {

            if (game.getDealCounter() % 2 == 0) {

                final Suit oldTrump = this.game.getTrump();

                do {
                    game.setTrump(Suit.values()[RAND.nextInt(Suit.values().length)]);
                }
                while (oldTrump == game.getTrump());

                game.getSay()
                    .clear();

//                userMessages.add(new UserMessage().message("Iedereen heeft gepast, nieuwe troef is: " + game.getTrump().symbol).variant(UserMessage.VariantEnum.INFO));
            } else {

                game.setTrump(Suit.values()[RAND.nextInt(Suit.values().length)]);
                game.getSay()
                    .clear();
                game.setPlayerCard(randomCards());

//                userMessages.add(new UserMessage().message("Iedereen heeft weer gepast, nieuwe kaarten").variant(UserMessage.VariantEnum.INFO));
            }

            game.setDealCounter(game.getDealCounter() + 1);

        }

        game.setUpdated(Instant.now());

//        return userMessages;

        return Mono.just(this);

    }

    @Override
    public boolean isCompleted() {
        return getTurnCount() >= 32;
    }

    private int whoHasCard(final Card card) {
        return game.getPlayerCard()
            .get(card);
    }

    private static boolean hasSuit(final List<Card> hand, final Suit suit) {
        return hand.stream()
            .map(Card::getSuit)
            .anyMatch(handCardSuit -> handCardSuit.equals(suit));
    }

    private static List<Card> getCardsOfSuit(final List<Card> hand, final Suit suit) {
        return hand.stream()
            .filter(card -> card.getSuit()
                .equals(suit))
            .collect(Collectors.toList());
    }

    private Integer getKlaverjassenValue(final Card c1) {
        return c1.getSuit()
            .equals(game.getTrump()) ? c1.getRank().trumpValue : c1.getRank().standardValue;
    }

    private int compareKlaverjassenCards(final Card card1, final Card card2) {
        return Integer.compare(getKlaverjassenValue(card1), getKlaverjassenValue(card2));
    }


    public Card getHighestCardInTrick(final List<Card> trick) {
        return trick.stream()
            .max(this::compareKlaverjassenCards)
            .orElseThrow();
    }

    private List<Card> getHand(final String userId) {
        final int playerNum = this.game.getPlayers()
            .indexOf(userId);
        return game.getPlayerCard()
            .entrySet()
            .stream()
            .filter(cardIntegerEntry -> cardIntegerEntry.getValue()
                .equals(playerNum))
            .map(Map.Entry::getKey)
            .filter(card -> !game.getTurns()
                .contains(card))
            .toList();
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

        try {
            return isAiPlayer(game.getPlayers()
                .get(calcWhoSay()));
        } catch (GameEngineException e) {
            return false;
        }

    }

    @Override
    public Game getGame() {
        return game;
    }

    public static boolean isAiPlayer(final String userId) {
        return AI_USER_ID.contains(userId);
    }

    boolean isErGegaan() {
        return game.getSay()
            .containsValue(Boolean.TRUE);
    }

    boolean niemandIsGegaanEnIedereenHeeftGezegd() {
        return iedereenHeeftGezegd() && !game.getSay()
            .containsValue(Boolean.TRUE);
    }

    boolean iedereenHeeftGezegd() {
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
                .reduce(0, (sum, current) -> sum + current + (trickNr == 7 ? 10 : 0));

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
                    default -> { }
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

            suitCards.sort(Comparator.comparing(card -> card.getRank().ordinal()));

            boolean isFourInARow = false;
            if (suitCards.size() == 4) {
                if (suitCards.get(0).getRank().ordinal() + 1 == suitCards.get(1).getRank().ordinal()
                    && suitCards.get(1).getRank().ordinal() + 1 == suitCards.get(2).getRank().ordinal()
                    && suitCards.get(2).getRank().ordinal() + 1 == suitCards.get(3).getRank().ordinal()) {
                    roem += 50;
                    isFourInARow = true;
                }
            }

            if (!isFourInARow && suitCards.size() >= 3) {
                for (int i = 0; i <= suitCards.size() - 3; i++) {
                    if (suitCards.get(i).getRank().ordinal() + 1 == suitCards.get(i + 1).getRank().ordinal()
                        && suitCards.get(i + 1).getRank().ordinal() + 1 == suitCards.get(i + 2).getRank().ordinal()) {
                        roem += 20;
                        break;
                    }
                }
            }
        }

        // Check for Stuk (King and Queen of trump)
        final Suit trumpSuit = game.getTrump();
        if (trick.stream().anyMatch(c -> c.getSuit() == trumpSuit && c.getRank() == Rank.KING)
            && trick.stream().anyMatch(c -> c.getSuit() == trumpSuit && c.getRank() == Rank.QUEEN)) {
            roem += 20;
        }

        return roem;
    }

    @Override
    public Boolean getErIsGegaan() {
        return this.game.getSay().containsValue(true);
    }

}
