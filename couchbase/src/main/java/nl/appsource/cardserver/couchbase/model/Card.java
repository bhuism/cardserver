package nl.appsource.cardserver.couchbase.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Card implements Comparable<Card> {

    Ac(Rank.ACE, Suit.Clubs),
    Kc(Rank.KING, Suit.Clubs),
    Qc(Rank.QUEEN, Suit.Clubs),
    Jc(Rank.JACK, Suit.Clubs),
    Tc(Rank.TEN, Suit.Clubs),
    Nc(Rank.NINE, Suit.Clubs),
    Ec(Rank.EIGHT, Suit.Clubs),
    Sc(Rank.SEVEN, Suit.Clubs),

    Ad(Rank.ACE, Suit.Diamonds),
    Kd(Rank.KING, Suit.Diamonds),
    Qd(Rank.QUEEN, Suit.Diamonds),
    Jd(Rank.JACK, Suit.Diamonds),
    Td(Rank.TEN, Suit.Diamonds),
    Nd(Rank.NINE, Suit.Diamonds),
    Ed(Rank.EIGHT, Suit.Diamonds),
    Sd(Rank.SEVEN, Suit.Diamonds),

    Ah(Rank.ACE, Suit.Hearts),
    Kh(Rank.KING, Suit.Hearts),
    Qh(Rank.QUEEN, Suit.Hearts),
    Jh(Rank.JACK, Suit.Hearts),
    Th(Rank.TEN, Suit.Hearts),
    Nh(Rank.NINE, Suit.Hearts),
    Eh(Rank.EIGHT, Suit.Hearts),
    Sh(Rank.SEVEN, Suit.Hearts),

    As(Rank.ACE, Suit.Spades),
    Ks(Rank.KING, Suit.Spades),
    Qs(Rank.QUEEN, Suit.Spades),
    Js(Rank.JACK, Suit.Spades),
    Ts(Rank.TEN, Suit.Spades),
    Ns(Rank.NINE, Suit.Spades),
    Es(Rank.EIGHT, Suit.Spades),
    Ss(Rank.SEVEN, Suit.Spades);

    public final Rank rank;

    public final Suit suit;

    public String getNiceString() {
        return rank.symbol + suit.symbol;
    }

}
