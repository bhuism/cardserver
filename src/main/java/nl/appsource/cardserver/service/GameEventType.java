package nl.appsource.cardserver.service;

public enum GameEventType {
    HUMAN_PLAY_CARD,
    HUMAN_SAY,
    AI_PLAY_CARD,
    AI_SAY,
    OPEN_LAST_TRICK,
    CLOSE_LAST_TRICK,
    CHECK_ROTATE
}
