package nl.appsource.cardserver.service.exception;

import org.openapitools.model.UserMessage;

public class NotAPlayerException extends GameEngineException {
    public NotAPlayerException() {
        super("You are not a player", UserMessage.VariantEnum.ERROR);
    }
}
