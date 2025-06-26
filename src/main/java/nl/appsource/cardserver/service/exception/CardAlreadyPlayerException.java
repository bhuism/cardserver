package nl.appsource.cardserver.service.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.model.Card;

@Getter
@RequiredArgsConstructor
public class CardAlreadyPlayerException extends GameEngineException {
    private final String gameId;
    private final Card card;
}
