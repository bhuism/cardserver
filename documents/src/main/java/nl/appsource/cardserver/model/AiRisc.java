package nl.appsource.cardserver.model;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum AiRisc {

    VERYLOW("verylow"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    VERYHIGH("veryhigh");

    public final String value;

}
