package nl.appsource.cardserver.openapi;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.generated.openapi.model.Card;
import nl.appsource.generated.openapi.model.GameEvent;
import nl.appsource.generated.openapi.model.PlayCardEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;


@Slf4j
public class TestSerDesPoliphormism {

    final JsonMapper jsonMapper = new JsonMapper();

    @Test
    public void testPlayCard() {

        final GameEvent gameEvent = PlayCardEvent.builder().card(Card.AS).build();


        log.info("gameEvent: " + jsonMapper.writeValueAsString(gameEvent));

        final GameEvent gameEvent2 = jsonMapper.readValue(jsonMapper.writeValueAsString(gameEvent), GameEvent.class);

        log.info("playCard: " + gameEvent2);


        assertEquals(gameEvent2, gameEvent);

    }

}
