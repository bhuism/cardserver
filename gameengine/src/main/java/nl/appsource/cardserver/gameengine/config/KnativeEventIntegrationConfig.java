package nl.appsource.cardserver.gameengine.config;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.generated.openapi.model.GameEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.integration.cloudevents.dsl.CloudEvents;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.http.support.DefaultHttpHeaderMapper;
import org.springframework.integration.webflux.dsl.WebFlux;

@Configuration
@EnableIntegration
@Slf4j
public class KnativeEventIntegrationConfig {

    @Bean
    public IntegrationFlow processOrderFlow() {

        DefaultHttpHeaderMapper headerMapper = DefaultHttpHeaderMapper.inboundMapper();
        // Ensure Knative CloudEvent binary headers are mapped into Spring Integration MessageHeaders
        headerMapper.setInboundHeaderNames("ce-*", "HTTP_REQUEST_HEADERS");

        return IntegrationFlow.from(WebFlux.inboundChannelAdapter("/gameEvent")
            .requestMapping(m -> m.methods(HttpMethod.POST)))
            .transform(CloudEvents.fromCloudEventTransformer())
            .handle(GameEvent.class, (gameEvent, headers) -> {
                log.info("Got game event: {} headers: {}", gameEvent.getEventType(), headers);
                return null;
            })
            .get();

//        return IntegrationFlow.from(WebFlux.inboundChannelAdapter("/gameEvent")
//                .requestMapping(m -> m.methods(HttpMethod.POST))
//                .transform(CloudEvents.fromCloudEventTransformer())
//                .requestPayloadType(GameEvent.class)
//                .headerMapper(headerMapper)
//            )
////            .handle(gameEventProcessor, "processGameEvent")
//            .handle(GameEvent.class, (gameEvent, headers) -> {
//                log.info("Got game event: {}", gameEvent.getEventType());
//                return null;
//            })
//            .get();

    }
}
