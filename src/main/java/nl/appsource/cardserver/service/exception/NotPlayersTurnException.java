package nl.appsource.cardserver.service.exception;

import org.openapitools.model.UserMessageMessage;

public class NotPlayersTurnException extends GameEngineException {
    public NotPlayersTurnException() {
        super("It's not your turn", UserMessageMessage.VariantEnum.WARNING);
    }
}
