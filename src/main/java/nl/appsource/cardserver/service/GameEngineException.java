package nl.appsource.cardserver.service;

public class GameEngineException extends RuntimeException {
    public GameEngineException() {
    }

    public GameEngineException(final String message) {
        super(message);
    }
}
