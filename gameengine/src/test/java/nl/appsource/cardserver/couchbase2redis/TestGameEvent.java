package nl.appsource.cardserver.couchbase2redis;

import nl.appsource.generated.openapi.model.GameEvent;
import org.junit.jupiter.api.Test;

import java.util.Optional;

public class TestGameEvent {

    @Test
    public void testGameEventNotEmpty() {
        final GameEvent gameEvent = new GameEvent().eventType(GameEvent.EventTypeEnum.CLOSE_LAST_TRICK).gameId("testGameId");
        org.junit.jupiter.api.Assertions.assertEquals(gameEvent.getCard(), Optional.empty());
    }

}
