package nl.appsource.cardserver.openapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import nl.appsource.cardserver.openapi.config.KafkaTopics;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

import static nl.appsource.cardserver.openapi.config.KafkaTopics.GAME_EVENTS_TOPIC;

@Slf4j
@RequiredArgsConstructor
public class KafkaSenderImpl implements KafkaSender {

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final JsonMapper jsonMapper;

    @Override
    public void send(final String topic, final String message) {

        kafkaTemplate.send(topic, message).whenComplete((result, exception) -> {
            if (exception == null) {
                log.info("Message sent successfully. Topic: {}, Partition: {}, Offset: {}",
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            } else {
                log.error("Failed to send message to topic: {}", GAME_EVENTS_TOPIC, exception);
            }
        });
    }


    @Override
    public void sendSse(final MyServerSentEvent<?> message) {
        send(KafkaTopics.SSE_TOPIC, jsonMapper.writeValueAsString(message));
    }
}
