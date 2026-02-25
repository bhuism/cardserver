package nl.appsource.cardserver.service.exception;

public class NotPlayersTurnException extends GameEngineException {
    public NotPlayersTurnException() {
        super("It's not your turn");
    }
}
