package nl.appsource.cardserver.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Card implements Comparable<Card> {

    Ac(Rank.Ace, Suit.Clubs),
    Kc(Rank.King, Suit.Clubs),
    Qc(Rank.Queen, Suit.Clubs),
    Jc(Rank.Jack, Suit.Clubs),
    Tc(Rank.Ten, Suit.Clubs),
    Nc(Rank.Nine, Suit.Clubs),
    Ec(Rank.Eight, Suit.Clubs),
    Sc(Rank.Seven, Suit.Clubs),

    Ad(Rank.Ace, Suit.Diamonds),
    Kd(Rank.King, Suit.Diamonds),
    Qd(Rank.Queen, Suit.Diamonds),
    Jd(Rank.Jack, Suit.Diamonds),
    Td(Rank.Ten, Suit.Diamonds),
    Nd(Rank.Nine, Suit.Diamonds),
    Ed(Rank.Eight, Suit.Diamonds),
    Sd(Rank.Seven, Suit.Diamonds),

    Ah(Rank.Ace, Suit.Hearts),
    Kh(Rank.King, Suit.Hearts),
    Qh(Rank.Queen, Suit.Hearts),
    Jh(Rank.Jack, Suit.Hearts),
    Th(Rank.Ten, Suit.Hearts),
    Nh(Rank.Nine, Suit.Hearts),
    Eh(Rank.Eight, Suit.Hearts),
    Sh(Rank.Seven, Suit.Hearts),

    As(Rank.Ace, Suit.Spades),
    Ks(Rank.King, Suit.Spades),
    Qs(Rank.Queen, Suit.Spades),
    Js(Rank.Jack, Suit.Spades),
    Ts(Rank.Ten, Suit.Spades),
    Ns(Rank.Nine, Suit.Spades),
    Es(Rank.Eight, Suit.Spades),
    Ss(Rank.Seven, Suit.Spades);

    private final Rank rank;

    private final Suit suit;

    @Override
    public String toString() {
        return rank.getSymbol() + suit.getSymbol();
    }

}
