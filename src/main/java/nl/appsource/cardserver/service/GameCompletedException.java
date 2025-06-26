package nl.appsource.cardserver.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public final class GameCompletedException extends GameEngineException {
    private final String gameId;
}
