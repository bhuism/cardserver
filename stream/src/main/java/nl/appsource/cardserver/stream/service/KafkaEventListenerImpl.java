package nl.appsource.cardserver.stream.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converters.service.GameToOpenApiConverter;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import org.reactivestreams.Publisher;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

@Slf4j
@Service
@Profile({"development", "production"})
@RequiredArgsConstructor
public class KafkaEventListenerImpl implements KafkaEventListener {

    private final JsonMapper jsonMapper;

    private final GameToOpenApiConverter gameToOpenApiConverter;

    private final Sinks.Many<Game> gamesChangedSink = Sinks.many().multicast().directBestEffort();

    @KafkaListener(topics = "couchbase-cardserver-events", groupId = "stream-KafkaEventListenerImpl-${HOSTNAME:local-dev}")
    public void listen(final @Header(KafkaHeaders.RECEIVED_KEY) String documentId, final @Payload(required = false) String documentPayload) {
        if (documentId == null || documentPayload == null) {
            return;
        }

        try {
            final JsonNode document = jsonMapper.readTree(documentPayload);
            final JsonNode classNode = document.get("_class");

            log.info("Got Kafka event: documentId={} _class={}", documentId, classNode);

            if (classNode != null && "nl.appsource.cardserver.model.Game".equals(classNode.asString())) {
                final Game game = jsonMapper.convertValue(document, Game.class);
                game.setId(documentId);

                gamesChangedSink.emitNext(game, Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1)));
            }
        } catch (final Exception e) {
            log.error("Error processing Kafka event: documentId={}", documentId, e);
        }
    }

    @Override
    public Publisher<MyServerSentEvent<nl.appsource.generated.openapi.model.Game>> gamesChanged(final String userId) {
        return gamesChangedSink.asFlux()
            .filter(game -> game.getPlayers().contains(userId))
            .map(game -> MyServerSentEvent.updateGame(gameToOpenApiConverter.convert(game)));
    }

}
