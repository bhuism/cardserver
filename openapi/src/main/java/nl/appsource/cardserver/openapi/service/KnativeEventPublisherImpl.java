package nl.appsource.cardserver.openapi.service;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.PojoCloudEventData;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.generated.openapi.model.GameEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.UUID;

@Slf4j
@Service
@Profile({"production", "development"})
@RequiredArgsConstructor
public class KnativeEventPublisherImpl implements KnativeEventPublisher {

    private WebClient webClient;
    private final JsonMapper jsonMapper;
    private final @Value("${K_SINK:http://kourier.impl.nl}") String brokerUrl;
    private final @Value("${spring.application.name:cardserver-api}") String source;
    private final WebClient.Builder webClientBuilder;

    @PostConstruct
    public void postConstruct() {
        this.webClient = webClientBuilder.build();
    }

    private Mono<ResponseEntity<Void>> publish(final CloudEvent event) {

        log.info("Publishing event to Knative eventing");

        return webClient.post()
            .uri(brokerUrl)
            .bodyValue(event)
            .retrieve()
            .toBodilessEntity();
    }

    @Override
    public Mono<ResponseEntity<Void>> publish(final GameEvent gameEvent) {
        return publish(createCloudEvent(gameEvent));
    }

    private CloudEvent createCloudEvent(final GameEvent gameEvent) {
        return CloudEventBuilder.v1()
            .withId(gameEvent.getUuid() != null ? gameEvent.getUuid().toString() : UUID.randomUUID().toString())
            .withType(gameEvent.getEventType().getValue())
            .withSource(URI.create(source.trim()))
            .withSubject(gameEvent.getGameId())
            .withData("application/json", PojoCloudEventData.wrap(gameEvent, data -> {
                try {
                    return jsonMapper.writeValueAsBytes(data);
                } catch (final JacksonException e) {
                    throw new RuntimeException(e);
                }
            }))
            .build();
    }
}
