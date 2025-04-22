package nl.appsource.cardserver.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Suit {
    Clubs(CardColor.BLACK), Diamonds(CardColor.RED), Hearts(CardColor.RED), Spades(CardColor.BLACK);
    private final CardColor color;
}
