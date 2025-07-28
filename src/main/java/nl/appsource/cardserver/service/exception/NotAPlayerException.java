package nl.appsource.cardserver.service.exception;

import org.openapitools.model.UserMessageMessage;

public class NotAPlayerException extends GameEngineException {
    public NotAPlayerException() {
        super("You are not a player", UserMessageMessage.VariantEnum.ERROR);
    }
}
