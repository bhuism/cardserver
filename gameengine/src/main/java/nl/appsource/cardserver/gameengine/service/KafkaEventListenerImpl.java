package nl.appsource.cardserver.gameengine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.generated.openapi.model.GameEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import static nl.appsource.cardserver.openapi.config.KafkaTopics.GAME_EVENTS_TOPIC;

@Slf4j
@Service
@Profile({"development", "production"})
@RequiredArgsConstructor
public class KafkaEventListenerImpl {

    private final JsonMapper jsonMapper;

    private final Worker worker;

    @KafkaListener(topics = GAME_EVENTS_TOPIC, groupId = "gameEngine-worker")
    public void listen(final @Payload String documentPayload) {

        try {
            final GameEvent gameEvent = jsonMapper.readValue(documentPayload, GameEvent.class);
            worker.scheduleGameEvent(gameEvent);
        } catch (final Exception e) {
            log.error("Error processing Kafka event: document={}", documentPayload, e);
        }

    }

}
