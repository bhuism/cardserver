package nl.appsource.cardserver.couchbase.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum AiRisc {

    VERYLOW("verylow"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    VERYHIGH("veryhigh");

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
