package nl.appsource.cardserver.service.exception;

public class GameEngineException extends RuntimeException {
    public GameEngineException() {
    }

    public GameEngineException(final String message) {
        super(message);
    }
}
