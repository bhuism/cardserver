package nl.appsource.cardserver.aiplayer.service;

import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Suit;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record Hand(List<Card> cards, Map<Suit, List<Card>> bySuit) {
    static Hand from(final List<Card> cards) {
        return new Hand(cards, cards.stream().collect(Collectors.groupingBy(Card::getSuit)));
    }

    List<Card> ofSuit(final Suit suit) {
        return bySuit.getOrDefault(suit, List.of());
    }

    boolean hasSuit(final Suit suit) {
        return bySuit.containsKey(suit);
    }
}
