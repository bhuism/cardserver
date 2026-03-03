package nl.appsource.cardserver.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ScreenOrientation {

    AUTO("auto"),
    PORTRAIT("portrait"),
    LANDSCAPE("landscape");

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
