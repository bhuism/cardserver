package nl.appsource.cardserver.stream.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("production")
public class KafkaEventListener {

    @KafkaListener(topics = "couchbase-cardserver-events", groupId = "cardserver-stream")
    public void listen(final @Header(KafkaHeaders.RECEIVED_KEY) String documentId, final @Payload(required = false) String documentPayload) {
        log.info("Received Kafka key: {}, documentPayload: {}  class: {}", documentId, documentPayload, documentPayload != null ? documentPayload.getClass() : "");
    }

}
