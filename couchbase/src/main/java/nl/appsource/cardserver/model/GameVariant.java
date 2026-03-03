package nl.appsource.cardserver.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum GameVariant {

    AMSTERDAMS("amsterdams"),
    ROTTERDAMS("rotterdams"),
    HAAGS("haags");

    public final String value;

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

}
