package nl.appsource.cardserver.stream.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("production")
public class KafkaEventListener {

    @KafkaListener(topics = "couchbase-cardserver-events")
    public void listen(final String event) {
        log.info("Received Kafka event: {}", event);
    }

}
