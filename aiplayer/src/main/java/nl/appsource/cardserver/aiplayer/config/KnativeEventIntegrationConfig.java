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
    public IntegrationFlow processCouchbaseEventsFlow() {

        final DefaultHttpHeaderMapper headerMapper = DefaultHttpHeaderMapper.inboundMapper();
        // Ensure Knative CloudEvent binary headers are mapped into Spring Integration MessageHeaders
        headerMapper.setInboundHeaderNames("*");

        return IntegrationFlow.from(WebFlux.inboundChannelAdapter("/couchbaseCardserverEvents")
                .requestMapping(m -> m.methods(HttpMethod.POST))
                .headerMapper(headerMapper)
                .errorChannel("couchbaseEventsErrorChannel")
            )
            .handle((event, headers) -> {
                log.info("Received couchbase event: {} (ce-id: {}, ce-type: {})",
                    event, headers.get("ce-id"), headers.get("ce-type"));
                return null;
            })
            .get();

    }

    @Bean
    public IntegrationFlow couchbaseEventsErrorFlow() {
        return IntegrationFlow.from("couchbaseEventsErrorChannel")
            .handle(m -> log.error("Error receiving couchbase event: {}", m.getPayload()))
            .get();
    }
}
