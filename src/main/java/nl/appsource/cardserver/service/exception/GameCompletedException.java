package nl.appsource.cardserver.service.exception;

import lombok.Getter;
import org.openapitools.model.UserMessageMessage;

@Getter
public final class GameCompletedException extends GameEngineException {
    public GameCompletedException() {
        super("Game has been completed", UserMessageMessage.VariantEnum.WARNING);
    }
}
