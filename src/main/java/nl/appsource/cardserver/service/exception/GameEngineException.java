package nl.appsource.cardserver.service.exception;

import lombok.Getter;

@Getter
public class GameEngineException extends RuntimeException {

    public GameEngineException(final String message) {
        super(message);
    }
}
