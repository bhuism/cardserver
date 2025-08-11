package nl.appsource.cardserver.service.exception;

import org.openapitools.model.UserMessage;

public class ElderException extends GameEngineException {
    public ElderException(String message, UserMessage.VariantEnum variantArg) {
        super(message, variantArg);
    }
}
