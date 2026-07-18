package nl.appsource.cardserver.gameengine.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.gameengine.service.Worker;
import nl.appsource.generated.openapi.model.GameEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.http.support.DefaultHttpHeaderMapper;
import org.springframework.integration.webflux.dsl.WebFlux;

@Configuration
@EnableIntegration
@Slf4j
@RequiredArgsConstructor
public class KnativeEventIntegrationConfig {

    private final Worker worker;

    @Bean
    public IntegrationFlow processGameEventFlow() {

        final DefaultHttpHeaderMapper headerMapper = DefaultHttpHeaderMapper.inboundMapper();
        // Ensure Knative CloudEvent binary headers are mapped into Spring Integration MessageHeaders
        headerMapper.setInboundHeaderNames("*");

        return IntegrationFlow.from(WebFlux.inboundChannelAdapter("/gameEvent")
                .requestMapping(m -> m.methods(HttpMethod.POST))
                .requestPayloadType(GameEvent.class)
                .headerMapper(headerMapper)
                .errorChannel("gameEventErrorChannel")
            )
            .handle(GameEvent.class, (gameEvent, headers) -> {
                log.info("Received game event: {} (ce-id: {}, ce-type: {})",
                    gameEvent, headers.get("ce-id"), headers.get("ce-type"));
                worker.scheduleGameEvent(gameEvent);
                return null;
            })
            .get();

    }

    @Bean
    public IntegrationFlow gameEventErrorFlow() {
        return IntegrationFlow.from("gameEventErrorChannel")
            .handle(m -> log.error("Error receiving game event: {}", m.getPayload()))
            .get();
    }
}
