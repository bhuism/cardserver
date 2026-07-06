package nl.appsource.cardserver.api.config;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Configuration
public class KnativeEventProcessor {

    @Bean
    public Function<CloudEvent, CloudEvent> processOrder() {

        log.info("processOrder bean created");

        return incomingEvent -> {

            // 1. Extract data and attributes from the incoming Knative event
            final String eventType = incomingEvent.getType();
            //byte[] rawData = incomingEvent.getData().toBytes();

            log.info("Processing incoming Knative event: {}", eventType);

            // 2. Process business logic...

            // 3. (Optional) Return a new CloudEvent to be sent back to the Broker
            return CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("https://spring-boot.my-cluster.local"))
                .withType("order.processed")
                .withData("application/json", "{\"status\": \"success\"}".getBytes(StandardCharsets.UTF_8))
                .build();
        };
    }
}
