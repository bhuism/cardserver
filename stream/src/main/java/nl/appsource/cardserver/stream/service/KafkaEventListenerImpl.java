package nl.appsource.cardserver.stream.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import org.reactivestreams.Publisher;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static nl.appsource.cardserver.openapi.config.KafkaTopics.SSE_TOPIC;

@Slf4j
@Service
@Profile({"development", "production"})
@RequiredArgsConstructor
public class KafkaEventListenerImpl implements KafkaEventListener {

    private final JsonMapper jsonMapper;

    private final Sinks.Many<MyServerSentEvent<?>> sseChannel = Sinks.many()
        .multicast()
        .directBestEffort();

    @KafkaListener(topics = SSE_TOPIC, groupId = "stream-KafkaEventListenerImpl-${HOSTNAME:local-dev}")
    public void listen(final @Payload String documentPayload) {

        try {
            final MyServerSentEvent<?> myServerSentEvent = jsonMapper.readValue(documentPayload, MyServerSentEvent.class);
            sseChannel.emitNext(myServerSentEvent, Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1)));
        } catch (final Exception e) {
            log.error("Error processing Kafka event: document={}", documentPayload, e);
        }

    }

    @Override
    public Publisher<MyServerSentEvent<?>> kafkaStreams() {
        return sseChannel.asFlux();
    }

}
