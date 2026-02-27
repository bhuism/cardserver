package nl.appsource.cardserver.couchbase.exception;

public class NotPlayersTurnException extends GameEngineException {
    public NotPlayersTurnException() {
        super("It's not your turn");
    }
}
