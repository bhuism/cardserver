package nl.appsource.cardserver.aiplayer.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Bean
    public IntegrationFlow processOrderFlow() {

        final DefaultHttpHeaderMapper headerMapper = DefaultHttpHeaderMapper.inboundMapper();
        // Ensure Knative CloudEvent binary headers are mapped into Spring Integration MessageHeaders
        headerMapper.setInboundHeaderNames("ce-*", "HTTP_REQUEST_HEADERS");

        return IntegrationFlow.from(WebFlux.inboundChannelAdapter("/couchbaseCardserverEvents")
                .requestMapping(m -> m.methods(HttpMethod.POST))
                //.requestPayloadType(GameEvent.class)
                .headerMapper(headerMapper)
            )
//            .handle(gameEventProcessor, "processGameEvent")
            .handle((event, headers) -> {
                log.info("Got data event: {} {} ", event != null ? event.getClass() : "", event);
                return null;
            })
            .get();

    }
}
