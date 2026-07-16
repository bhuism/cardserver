package nl.appsource.cardserver.stream.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converters.service.BoomToOpenApiConverter;
import nl.appsource.cardserver.converters.service.GameToOpenApiConverter;
import nl.appsource.cardserver.model.Boom;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import org.reactivestreams.Publisher;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
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
    private final Sinks.Many<Boom> boomChangedSink = Sinks.many().multicast().directBestEffort();
    private final BoomToOpenApiConverter boomToOpenApiConverter;

    @KafkaListener(topics = "couchbase-cardserver-events", groupId = "cardserver-local-stream")
    public void listen(final @Header(KafkaHeaders.RECEIVED_KEY) String documentId, final @Payload(required = false) String documentPayload) {
        if (documentId == null || documentPayload == null) {
            return;
        }

        try {
            final JsonNode document = jsonMapper.readTree(documentPayload);
            final JsonNode classNode = document.get("_class");

            if (classNode != null && "nl.appsource.cardserver.model.Game".equals(classNode.asString())) {
                final Game game = jsonMapper.convertValue(document, Game.class);
                game.setId(documentId);
                gamesChangedSink.emitNext(game, Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1)));
            } else if (classNode != null && "nl.appsource.cardserver.model.Boom".equals(classNode.asString())) {
                final Boom boom = jsonMapper.convertValue(document, Boom.class);
                boom.setId(documentId);
                boomChangedSink.emitNext(boom, Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1)));
            }

        } catch (final Exception e) {
            log.error("Error processing Kafka event: documentId={}", documentId, e);
        }
    }

    @Override
    public Publisher<MyServerSentEvent<?>> couchbaseSubscribe(final String userId) {
        return Flux.merge(gamesChangedSink.asFlux()
                .filter(game -> game.getPlayers().contains(userId))
                .map(game -> MyServerSentEvent.updateGame(gameToOpenApiConverter.convert(game))),
            boomChangedSink.asFlux()
                .filter(boom -> boom.getPlayers().contains(userId))
                .map(boom -> MyServerSentEvent.updateBoom(boomToOpenApiConverter.convert(boom))));
    }

}
