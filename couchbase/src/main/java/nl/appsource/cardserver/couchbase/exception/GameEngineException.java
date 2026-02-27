package nl.appsource.cardserver.couchbase.exception;

import lombok.Getter;

@Getter
public class GameEngineException extends RuntimeException {

    public GameEngineException(final String message) {
        super(message);
    }
}
