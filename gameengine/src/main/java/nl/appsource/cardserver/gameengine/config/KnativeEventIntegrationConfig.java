package nl.appsource.cardserver.gameengine.config;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.generated.openapi.model.GameEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.webflux.dsl.WebFlux;
import org.springframework.messaging.Message;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Configuration
@Slf4j
@EnableIntegration
public class KnativeEventIntegrationConfig {

    @Bean
    public IntegrationFlow processOrderFlow() {
        return IntegrationFlow.from(WebFlux.inboundGateway("/gameEvent")
                .requestMapping(m -> m.methods(HttpMethod.POST))
                // Let WebFlux automatically deserialize the JSON body into your POJO
                .requestPayloadType(GameEvent.class)
                .mappedResponseHeaders("ce-*", "Content-Type"))

            .handle(Message.class, (message, headers) -> {
                // The payload is now your strongly-typed business class
                final GameEvent gameEvent = (GameEvent) message.getPayload();
//                String eventType = (String) headers.get("ce-type");

                log.info("Received event: {} id: {}", gameEvent.getEventType(), headers.get("Ce-Id"));

                return processReactively(gameEvent)
                    .map(resultPojo -> MessageBuilder.withPayload(resultPojo)
                        .setHeader("ce-id", UUID.randomUUID().toString())
                        // ... other headers
                        .build());

            })
            .get();
    }

    private Mono<Object> processReactively(final GameEvent orderEvent) {
        return Mono.just(new Object());
    }

}
