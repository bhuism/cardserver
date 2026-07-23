package nl.appsource.cardserver.aiplayer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(nl.appsource.cardserver.openapi.config.KafkaConfig.class)
public class KafkaConfig {
}
