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
import nl.appsource.cardserver.service.exception.NeedNewSayRound;
import nl.appsource.cardserver.service.exception.NoElderException;
import nl.appsource.cardserver.service.exception.NotAPlayerException;
import nl.appsource.cardserver.service.exception.NotPlayersTurnException;
import org.openapitools.model.UserMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

@Slf4j
public record GameEngineImpl(Game game) implements GameEngine {

    public static final List<String> AI_USER_ID = List.of("2ab5fd69a2796c4740380cd98eb7", "2ab5fd69a2796c4740380cd98eb8", "2ab5fd69a2796c4740380cd98eb9");

    //    private static final Map<Rank, Integer> RANK_REGULAR = Map.of(Rank.Ace, 8, Rank.King, 6, Rank.Queen, 5, Rank.Jack, 4, Rank.Ten, 7, Rank.Nine, 3, Rank.Eight, 2, Rank.Seven, 1);
//
//    private static final Map<Rank, Integer> RANK_TRUMP = Map.of(Rank.Ace, 14, Rank.King, 12, Rank.Queen, 11, Rank.Jack, 16, Rank.Ten, 13, Rank.Nine, 15, Rank.Eight, 10, Rank.Seven, 9);

    private static final Comparator<? super Card> TRUMP_SORTER = comparing(o -> o.rank.trumpValue);

    private static final Comparator<? super Card> REGULAR_SORTER = comparing(o -> o.rank.standardValue);

    @Override
    public int calcTricksPlayed() {
        return this.game.getTurns().size() / 4;
    }

    @Override
    public boolean hasFullTrick() {
        return this.game.getTurns().size() >= 4 && this.game.getTurns().size() % 4 == 0;
    }

    private List<Card> getTrickCards(final int trickNr) {
        return game.getTurns().subList(trickNr * 4, Math.min(game.getTurns().size(), trickNr * 4 + 4));
    }

    private Card determineTrickWinningCard(final int trickNr) throws GameEngineException {

        if (trickNr >= calcTricksPlayed() || trickNr < 0) {
            throw new GameEngineException("no such trick " + trickNr);
        }

        final List<Card> trick = getTrickCards(trickNr);

        if (trick.size() != 4) {
            throw new GameEngineException("Not 5 trick cards in tick " + trickNr);
        }

        final boolean troefAanwezig = trick.stream().anyMatch(c -> c.getSuit().equals(game.getTrump()));

        final Suit requestedSuit = trick.getFirst().getSuit();

        return trick.stream()
            .filter(c -> c.getSuit().equals(troefAanwezig ? game.getTrump() : requestedSuit))
            .max(troefAanwezig ? TRUMP_SORTER : REGULAR_SORTER)
            .orElseThrow(() -> new GameEngineException("determineTrickWinningCard() No card found"));

    }

    int determineTrickWinner(final int trickNr) throws GameEngineException {
        final Card winningCard = determineTrickWinningCard(trickNr);
        return whoHasCard(winningCard);
    }

    public int calcWhoSay() throws GameEngineException {

        if (isCompleted()) {
            throw new GameCompletedException();
        }

        if (game.getSay() == null) {
            return (game.getDealer() + 1) % 4;
        }

        if (game.getSay().containsValue(Boolean.TRUE)) {
            throw new ElderException(null);
        }

        if (game.getSay().size() <= 3) {
            return (game.getDealer() + 1 + game.getSay().size()) % 4;
        }

        throw new NeedNewSayRound(null);

    }

    @Override
    public int calcWhoHasTurn() throws GameEngineException {

        if (isCompleted()) {
            throw new GameCompletedException();
        }

        if (game.getSay() == null || !game.getSay().containsValue(Boolean.TRUE)) {
            throw new NoElderException(null);
        }

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
    public void playAiCard() throws GameEngineException {

        if (isCompleted()) {
            throw new GameCompletedException();
        }

        if (!isAiTurn()) {
            throw new IllegalStateException("Not ai's turn to play a card");
        }

        final String userId = game.getPlayers().get(calcWhoHasTurn());

        playCard(userId, calcAiCard(userId));

    }

    @Override
    public void sayAi() throws GameEngineException {

        if (isCompleted()) {
            throw new GameCompletedException();
        }

        if (!isAiSay()) {
            throw new IllegalStateException("Not ai's turn to say");
        }

        final int whoSay = calcWhoSay();

        final String userId = this.game.getPlayers().get(whoSay);

        say(userId, decideBid(userId));

    }

    @Override
    public List<UserMessage> playCard(final String userId, final Card card) throws GameEngineException {

        final List<UserMessage> userMessages = new ArrayList<>();

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

        final List<Card> currentTrick = getTrickCards(calcTricksPlayed());

        if (!currentTrick.isEmpty()) {
            final Card leadingCard = currentTrick.getFirst();
            if (leadingCard.getSuit() != card.getSuit() && getHand(userId).stream().anyMatch(c -> c.getSuit().equals(leadingCard.getSuit()))) {
                userMessages.add(new UserMessage().message("U heeft verzaakt").variant(UserMessage.VariantEnum.WARNING));
            }
        }

        log.info("playCard() game: {}, card: {}, player: {}", game.getId(), card, userId);

        game.setUpdated(Instant.now());
        game.getTurns().add(card);

        return userMessages;

    }


    @Override
    public List<UserMessage> say(final String userId, final Boolean say) throws GameEngineException {

        final List<UserMessage> userMessages = new ArrayList<>();

        final int playerNum = this.game.getPlayers().indexOf(userId);

        if (isCompleted()) {
            log.warn("Game {} allready completed", game.getId());
            throw new GameCompletedException();
        }

        if (!game.getPlayers().contains(userId)) {
            throw new NotAPlayerException();
        }

        if (game.getSay() == null) {
            game.setSay(new HashMap<>());
        }

        if (game.getSay().containsValue(Boolean.TRUE)) {
            throw new ElderException("Er is al iemand gegaan");
        }

        if (game.getSay().containsKey(playerNum)) {
            throw new ElderException("Je hebt al gezegd");
        }

        if (game.getSay().size() == 4) {
            throw new NeedNewSayRound(null);
        }

        final int whoSay = calcWhoSay();

        if (whoSay != playerNum) {
            log.warn("say() It's player {} turn to say", game.getPlayers().get(whoSay));
            throw new NotPlayersTurnException();
        }

        game.setUpdated(Instant.now());
        game.getSay().put(playerNum, say);

        return userMessages;
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
        return c1.getSuit().equals(game.getTrump()) ? c1.getRank().trumpValue : c1.getRank().standardValue;
    }

    private int compareKlaverjassenCards(final Card card1, final Card card2) {
        return Integer.compare(getKlaverjassenValue(card1), getKlaverjassenValue(card2));
    }


    public Card getHighestCardInTrick(final List<Card> trick) {
        if (trick.isEmpty()) {
            return null;
        }
        return trick.stream().max(this::compareKlaverjassenCards).orElseThrow();
    }

//    @Override
//    public void playAiCard() {
//
//        final int gotTurn = calcWhoHasTurn();
//
//        final String aiUserId = game.getPlayers().get(gotTurn);
//
//        if (!AI_USER_ID.contains(aiUserId)) {
//            throw new GameEngineException("Player who is aan slag is not an AI player");
//        }
//
//        final Card card = calcAiCard(aiUserId);
//
//        log.info("Ai {} plays card {}", aiUserId, card);
//
//        playCard(aiUserId, card);
//
//    }

    private List<Card> getHand(final String userId) {
        final int playerNum = this.game.getPlayers().indexOf(userId);
        return game.getPlayerCard().entrySet().stream().filter(cardIntegerEntry -> cardIntegerEntry.getValue().equals(playerNum)).map(Map.Entry::getKey).filter(card -> !game.getTurns().contains(card)).toList();
    }

    public Card calcAiCard(final String userId) throws GameEngineException {

//        log.info("calcAiCard() userId={}", userId);

        if (!AI_USER_ID.contains(userId)) {
            throw new GameEngineException("Not an Ai player");
        }


        final boolean isFirstPlayerInTrick = game.getTurns().size() % 4 == 0;

        final List<Card> hand = getHand(userId);

        // If leading the trick, play the lowest card of a non-trump suit, or lowest trump if only trumps.
        if (isFirstPlayerInTrick) {
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


    private static final int BIDDING_THRESHOLD = 20;


    public boolean decideBid(final String userId) {

        final List<Card> hand = getHand(userId);

        int currentScore = 0;

        final List<Card> trumpCards = hand.stream().filter(c -> c.suit == game.getTrump()).toList();
        final Map<Rank, Long> trumpRanks = trumpCards.stream().collect(Collectors.groupingBy(c -> c.rank, Collectors.counting()));

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

        log.info("{}: evaluates their hand for trump: {}  with score: {}", userId, game.getTrump(), currentScore);

        return currentScore >= BIDDING_THRESHOLD;

    }

    @Override
    public boolean isAiTurn() {

        if (isCompleted()) {
            return false;
        }

        if (game.getSay() == null || !game.getSay().containsValue(Boolean.TRUE)) {
            return false;
        }

        try {
            return isAiPlayer(game.getPlayers().get(calcWhoHasTurn()));
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

        if (game.getSay().containsValue(Boolean.TRUE)) {
            return false;
        }

        if (game.getSay().size() == 4) {
            return false;
        }

        try {
            return isAiPlayer(game.getPlayers().get(calcWhoSay()));
        } catch (GameEngineException e) {
            return false;
        }

    }

    boolean isAiPlayer(final String userId) {
        return AI_USER_ID.contains(userId);
    }

}
