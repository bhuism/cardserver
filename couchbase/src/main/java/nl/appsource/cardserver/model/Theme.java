package nl.appsource.cardserver.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum Theme {

    AUTO("auto"),
    LIGHT("light"),
    DARK("dark");

    final String value;

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

}
