package nl.appsource.cardserver.couchbase.exception;

public class NotAPlayerException extends GameEngineException {
    public NotAPlayerException() {
        super("You are not a player");
    }
}
