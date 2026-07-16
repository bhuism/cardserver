package nl.appsource.cardserver.stream.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

@Slf4j
@Service
@Profile({"development", "production"})
@RequiredArgsConstructor
public class KafkaEventListener {

    private final JsonMapper jsonMapper;

    @KafkaListener(topics = "couchbase-cardserver-events", groupId = "cardserver-cluster-stream")
    public void listen(final @Header(KafkaHeaders.RECEIVED_KEY) String documentId, final @Payload(required = false) String documentPayload) {
        log.info("Received Kafka key: {}, documentPayload: {}  class: {}", documentId, documentPayload, documentPayload != null ? documentPayload.getClass() : "");

        Optional.ofNullable(documentPayload)
            .map(jsonMapper::readTree)
            .ifPresent(document -> log.info("Received document: {}", document));

    }

}
