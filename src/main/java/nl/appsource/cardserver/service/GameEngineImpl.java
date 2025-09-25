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
import nl.appsource.cardserver.service.exception.NoElderException;
import nl.appsource.cardserver.service.exception.NotAPlayerException;
import nl.appsource.cardserver.service.exception.NotPlayersTurnException;
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

    int determineTrickWinner(final int trickNr) throws GameEngineException {

        if (trickNr >= calcTricksPlayed() || trickNr < 0) {
            throw new GameEngineException("no such trick " + trickNr);
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
    public int calcWhoHasTurn() throws GameEngineException {

        if (isCompleted()) {
            throw new GameCompletedException();
        }

        if (game.getSay() == null || !isErGegaan()) {
            throw new NoElderException(null);
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

    public Card calcAiCard(final String userId) throws GameEngineException {

//        log.info("calcAiCard() userId={}", userId);

        if (!AI_USER_ID.contains(userId)) {
            throw new GameEngineException("Not an Ai player");
        }

        final List<Card> hand = getHand(userId);

        // If leading the trick, play the lowest card of a non-trump suit, or lowest trump if only trumps.
        if (isFirstTrickCard()) {
            // Try to play a low card from a non-trump suit
            for (Card card : hand) {
                if (card.getSuit() != game.getTrump() && card.getRank() != Rank.SEVEN && card.getRank() != Rank.EIGHT) {
                    return card;
                }
            }
            // If only trumps or high cards, play the lowest card
            return hand.getFirst();
        } else {

            final List<Card> currentTrick = getTrickCards(calcTricksPlayed());


            // Not the first player, must follow suit if possible
            Card leadingCard = currentTrick.getFirst();
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
                    playableCards.sort((c1, c2) -> {
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
                    return playableCards.getFirst(); // Play lowest if cannot beat
                } else {
                    // If has suit but no card can beat, play the lowest of the leading suit
                    cardsOfLeadingSuit.sort((c1, c2) -> {
                        int val1 = getKlaverjassenValue(c1);
                        int val2 = getKlaverjassenValue(c2);
                        return Integer.compare(val1, val2);
                    });
                    return cardsOfLeadingSuit.getFirst();
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
                        playableCards.sort((c1, c2) -> {
                            int val1 = getKlaverjassenValue(c1);
                            int val2 = getKlaverjassenValue(c2);
                            return Integer.compare(val1, val2);
                        });
                        return playableCards.getFirst();
                    } else {
                        // If has trump but cannot beat, play the lowest trump
                        trumpCards.sort((c1, c2) -> {
                            int val1 = getKlaverjassenValue(c1);
                            int val2 = getKlaverjassenValue(c2);
                            return Integer.compare(val1, val2);
                        });
                        return trumpCards.getFirst();
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
                    return hand.getFirst();
                }
            }
        }
    }

    private static final int BIDDING_THRESHOLD = 24; // 25

    public boolean decideBid(final String userId) {

        final List<Card> hand = getHand(userId);

        int currentScore = 0;

        final List<Card> trumpCards = hand.stream()
            .filter(c -> c.suit == game.getTrump())
            .toList();
        final Map<Rank, Long> trumpRanks = trumpCards.stream()
            .collect(Collectors.groupingBy(c -> c.rank, Collectors.counting()));

        // Points for high trumps
        if (trumpRanks.containsKey(Rank.JACK)) {
            currentScore += 10;
        }
        if (trumpRanks.containsKey(Rank.NINE)) {
            currentScore += 8;
        }

        // Points for length (more than 3 trumps is good)
        if (trumpCards.size() > 3) {
            currentScore += (trumpCards.size() - 3) * 3;
        }

        // Points for a "marriage" (King & Queen of trump)
        if (trumpRanks.containsKey(Rank.KING) && trumpRanks.containsKey(Rank.QUEEN)) {
            currentScore += 5;
        }

        // Points for Aces in side suits
        for (Card card : hand) {
            if (card.suit != game.getTrump() && card.rank == Rank.ACE) {
                currentScore += 6;
            }
        }

        final boolean decision = currentScore >= BIDDING_THRESHOLD;

        log.info("{}: evaluates their hand for trump: {}, with score: {}, decision={}", userId, game.getTrump(), currentScore, decision);

        return decision;

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

        try {
            return isAiPlayer(game.getPlayers()
                .get(calcWhoHasTurn()));
        } catch (GameEngineException e) {
            return false;
        }

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
}
