package nl.appsource.cardserver.service.exception;

import lombok.Getter;
import org.openapitools.model.UserMessageMessage;

@Getter
public class GameEngineException extends RuntimeException {

    private final UserMessageMessage.VariantEnum variant;

    public GameEngineException(final String message, final UserMessageMessage.VariantEnum variantArg) {
        super(message);
        this.variant = variantArg;
    }
}
