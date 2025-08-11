package nl.appsource.cardserver.service.exception;

import org.openapitools.model.UserMessage;

public class NoElderException extends GameEngineException {
    public NoElderException(final String message, final UserMessage.VariantEnum variantArg) {
        super(message, variantArg);
    }
}
