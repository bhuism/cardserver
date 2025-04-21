package nl.appsource.cardserver.model;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum DeckCard {

    Ac(CardNr.Ace, Suit.Clubs),
    Kc(CardNr.King, Suit.Clubs),
    Qc(CardNr.Queen, Suit.Clubs),
    Jc(CardNr.Jack, Suit.Clubs),
    Tc(CardNr.Ten, Suit.Clubs),
    Nc(CardNr.Nine, Suit.Clubs),
    Ec(CardNr.Eight, Suit.Clubs),
    Sc(CardNr.Seven, Suit.Clubs),

    Ad(CardNr.Ace, Suit.Diamonds),
    Kd(CardNr.King, Suit.Diamonds),
    Qd(CardNr.Queen, Suit.Diamonds),
    Jd(CardNr.Jack, Suit.Diamonds),
    Td(CardNr.Ten, Suit.Diamonds),
    Nd(CardNr.Nine, Suit.Diamonds),
    Ed(CardNr.Eight, Suit.Diamonds),
    Sd(CardNr.Seven, Suit.Diamonds),

    Ah(CardNr.Ace, Suit.Hearts),
    Kh(CardNr.King, Suit.Hearts),
    Qh(CardNr.Queen, Suit.Hearts),
    Jh(CardNr.Jack, Suit.Hearts),
    Th(CardNr.Ten, Suit.Hearts),
    Nh(CardNr.Nine, Suit.Hearts),
    Eh(CardNr.Eight, Suit.Hearts),
    Sh(CardNr.Seven, Suit.Hearts),

    As(CardNr.Ace, Suit.Spades),
    Ks(CardNr.King, Suit.Spades),
    Qs(CardNr.Queen, Suit.Spades),
    Js(CardNr.Jack, Suit.Spades),
    Ts(CardNr.Ten, Suit.Spades),
    Ns(CardNr.Nine, Suit.Spades),
    Es(CardNr.Eight, Suit.Spades),
    Ss(CardNr.Seven, Suit.Spades),
    ;

    private final CardNr cardNr;

    private final Suit suit;
}
