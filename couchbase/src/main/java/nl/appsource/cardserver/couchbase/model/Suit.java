package nl.appsource.cardserver.couchbase.model;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum Suit {

    Clubs(CardColor.BLACK, "♣"),
    Diamonds(CardColor.RED, "♦"),
    Hearts(CardColor.RED, "♥"),
    Spades(CardColor.BLACK, "♠");

    public final CardColor color;
    public final String symbol;
}
