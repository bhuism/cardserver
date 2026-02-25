package nl.appsource.cardserver.service.exception;

import lombok.Getter;
import nl.appsource.cardserver.model.Card;

@Getter
public class CardAlreadyPlayerException extends GameEngineException {
    public CardAlreadyPlayerException(final Card card) {
        super("Card " + card + " has already been played");
    }
}
