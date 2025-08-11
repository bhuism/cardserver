package nl.appsource.cardserver.service.exception;

public class NotAPlayerException extends GameEngineException {
    public NotAPlayerException() {
        super("You are not a player");
    }
}
