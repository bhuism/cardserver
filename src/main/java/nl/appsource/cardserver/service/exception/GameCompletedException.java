package nl.appsource.cardserver.service.exception;

import lombok.Getter;

@Getter
public final class GameCompletedException extends GameEngineException {
    public GameCompletedException() {
        super("Game has been completed");
    }
}
