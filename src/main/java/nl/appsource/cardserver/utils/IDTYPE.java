package nl.appsource.cardserver.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IDTYPE {

    USER("user"),
    GAME("game"),
    BOOM("boom"),
    FEED("feed"),
    SESS("sess");

    private final String identifier;

}
