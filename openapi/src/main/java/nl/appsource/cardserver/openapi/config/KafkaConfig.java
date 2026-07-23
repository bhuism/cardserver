package nl.appsource.cardserver.openapi.config;

import nl.appsource.cardserver.openapi.service.KafkaSender;
import nl.appsource.cardserver.openapi.service.KafkaSenderImpl;
import nl.appsource.cardserver.openapi.service.SseEventSender;
import nl.appsource.cardserver.openapi.service.SseEventSenderImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class KafkaConfig {

    @Bean
    public KafkaSender kafkaSender(final KafkaTemplate kafkaTemplate, final JsonMapper jsonMapper) {
        return new KafkaSenderImpl(kafkaTemplate, jsonMapper);
    }

    @Bean
    public SseEventSender sseEventSender(final KafkaTemplate kafkaTemplate) {
        return new SseEventSenderImpl(kafkaTemplate);
    }
}
