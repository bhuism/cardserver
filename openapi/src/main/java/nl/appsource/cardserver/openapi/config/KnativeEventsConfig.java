package nl.appsource.cardserver.openapi.config;

import nl.appsource.cardserver.openapi.service.KnativeEventPublisher;
import nl.appsource.cardserver.openapi.service.KnativeEventPublisherImpl;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

@EnableConfigurationProperties(KnativeProperties.class)
public class KnativeEventsConfig {

    @Bean
    public KnativeEventPublisher knativeEventPublisher(
        final WebClient.Builder webClientBuilder,
        final JsonMapper jsonMapper,
        final KnativeProperties knativeProperties
    ) {
        return new KnativeEventPublisherImpl(webClientBuilder, jsonMapper, knativeProperties);
    }

}
