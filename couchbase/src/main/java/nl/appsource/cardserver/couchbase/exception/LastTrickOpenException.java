package nl.appsource.cardserver.couchbase.exception;

import lombok.Getter;

@Getter
public class LastTrickOpenException extends GameEngineException {
    public LastTrickOpenException() {
        super("Last trick is open");
    }
}
