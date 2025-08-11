package nl.appsource.cardserver.service.exception;

import org.openapitools.model.UserMessage;

public class NeedNewSayRound extends GameEngineException {
    public NeedNewSayRound(final String message, final UserMessage.VariantEnum variantArg) {
        super(message, variantArg);
    }
}
