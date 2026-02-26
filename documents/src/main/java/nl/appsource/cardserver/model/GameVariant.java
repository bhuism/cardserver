package nl.appsource.cardserver.model;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum GameVariant {

    AMSTERDAMS("amsterdams"),
    ROTTERDAMS("rotterdams"),
    HAAGS("haags");

    public final String value;
}
