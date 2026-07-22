package nl.appsource.cardserver.stream.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converters.service.BoomToOpenApiConverter;
import nl.appsource.cardserver.converters.service.GameToOpenApiConverter;
import nl.appsource.cardserver.converters.service.UserToOpenApiConverter;
import nl.appsource.cardserver.model.Boom;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import org.reactivestreams.Publisher;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.Set;

import static nl.appsource.cardserver.openapi.config.KafkaTopics.COUCHBASE_CARDSERVER_EVENTS;

@Slf4j
@Service
@Profile({"development", "production"})
@RequiredArgsConstructor
public class KafkaEventListenerImpl implements KafkaEventListener {

    private final JsonMapper jsonMapper;

    private final GameToOpenApiConverter gameToOpenApiConverter;

    private final BoomToOpenApiConverter boomToOpenApiConverter;

    private final UserToOpenApiConverter userToOpenApiConverter;

    private final Sinks.Many<ObjectNode> gamesChangedSink = Sinks.many().multicast().directBestEffort();

    @KafkaListener(topics = COUCHBASE_CARDSERVER_EVENTS, groupId = "stream-KafkaEventListenerImpl-${HOSTNAME:local-dev}")
    public void listen(final @Header(KafkaHeaders.RECEIVED_KEY) String documentId, final @Payload(required = false) String documentPayload) {

        if (documentId == null || documentPayload == null) {
            return;
        }

        try {
            final ObjectNode document = (ObjectNode) jsonMapper.readTree(documentPayload);
            document.put("id", documentId);
            log.info("Got Kafka event: documentId={}", documentId);
            gamesChangedSink.emitNext(document, Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1)));
        } catch (final Exception e) {
            log.error("Error processing Kafka event: documentId={}", documentId, e);
        }
    }

    private static final Set<Class> CLASSES = Set.of(Game.class, Boom.class, User.class);

    @Override
    public Publisher<MyServerSentEvent<?>> kafkaStreams() {
        return gamesChangedSink.asFlux()
            .flatMap(document -> {
                final JsonNode classNode = document.get("_class");

                if (classNode != null) {

                    final String stringClazz = classNode.asString();

                    if (StringUtils.hasText(stringClazz) && stringClazz.startsWith("nl.appsource.cardserver.model.")) {

                        try {
                            final Class<?> clazz = Class.forName(stringClazz);

                            final Object entity = jsonMapper.convertValue(document, clazz);

                            if (entity instanceof final Game game) {
                                return Mono.just(MyServerSentEvent.updateGame(gameToOpenApiConverter.convert(game)));
                            } else if (entity instanceof final Boom boom) {
                                return Mono.just(MyServerSentEvent.updateBoom(boomToOpenApiConverter.convert(boom)));
                            } else if (entity instanceof final User user) {
                                return Mono.just(MyServerSentEvent.updateUser(userToOpenApiConverter.convert(user), Set.of(((User) entity).getId())));
                            }
                        } catch (final Exception e) {
                            log.error("Error processing Kafka event: documentId={}", document.get("id"), e);
                        }

                    }
                }

                return Mono.empty();

            });
    }

}
