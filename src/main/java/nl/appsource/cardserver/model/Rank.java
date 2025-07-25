package nl.appsource.cardserver.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Rank {

    Ace("A"), King("K"), Queen("Q"), Jack("J"), Ten("10"), Nine("9"), Eight("8"), Seven("7");

    private final String symbol;
}
