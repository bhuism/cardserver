package nl.appsource.cardserver.service.exception;

import lombok.Getter;
import org.openapitools.model.UserMessage;

@Getter
public class GameEngineException extends Exception {

    private final UserMessage.VariantEnum variant;

    public GameEngineException(final String message, final UserMessage.VariantEnum variantArg) {
        super(message);
        this.variant = variantArg;
    }
}
