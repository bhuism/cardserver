package nl.appsource.cardserver.service.exception;

import org.openapitools.model.UserMessage;

public class NeedNewSayRound extends GameEngineException {
    public NeedNewSayRound(String message, UserMessage.VariantEnum variantArg) {
        super(message, variantArg);
    }
}
