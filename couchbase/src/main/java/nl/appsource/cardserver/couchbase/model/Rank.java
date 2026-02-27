package nl.appsource.cardserver.couchbase.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum Rank {

    ACE("A", 11, 11),
    KING("K", 4, 4),
    QUEEN("Q", 3, 3),
    JACK("J", 2, 20),
    TEN("10", 10, 10),
    NINE("9", 0, 14),
    EIGHT("8", 0, 0),
    SEVEN("7", 0, 0);

    public final String symbol;
    public final int standardValue;
    public final int trumpValue;

}
