package nl.appsource.cardsever.api.service.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardsever.api.service.GameEventType;

import java.io.Serializable;


@Getter
@Accessors(chain = true)
@RequiredArgsConstructor
@ToString
public class ScheduledGameEvent implements Serializable {

    private final long executionTime;

    private final String userId;

    private final GameEventType gameEventType;

    private final String gameId;

    @Setter
    private Card card;

    @Setter
    private Boolean say;

}
