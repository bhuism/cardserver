package nl.appsource.cardserver.service.exception;

import org.openapitools.model.UserMessage;

public class NotPlayersTurnException extends GameEngineException {
    public NotPlayersTurnException() {
        super("It's not your turn", UserMessage.VariantEnum.WARNING);
    }
}
