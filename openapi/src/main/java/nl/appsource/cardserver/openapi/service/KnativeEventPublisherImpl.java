package nl.appsource.cardserver.openapi.service;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.PojoCloudEventData;
import io.cloudevents.spring.http.CloudEventHttpUtils;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.openapi.config.KnativeProperties;
import nl.appsource.generated.openapi.model.GameEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.UUID;

@Slf4j
public class KnativeEventPublisherImpl implements KnativeEventPublisher {

    private final WebClient webClient;
    private final JsonMapper jsonMapper;
    private final KnativeProperties knativeProperties;

    public KnativeEventPublisherImpl(final WebClient.Builder webClientBuilder,
                                     final JsonMapper jsonMapper,
                                     final KnativeProperties knativeProperties) {
        this.webClient = webClientBuilder.build();
        this.jsonMapper = jsonMapper;
        this.knativeProperties = knativeProperties;
    }

    private Mono<ResponseEntity<Void>> publish(final CloudEvent event) {

        log.info("Publishing event to Knative eventing: {}", event.getType());

        return webClient.post()
            .uri(knativeProperties.getSink())
            .headers(headers -> {
                headers.addAll(CloudEventHttpUtils.toHttp(event));
                if (event.getDataContentType() != null) {
                    headers.set("Content-Type", event.getDataContentType());
                }
            })
            .bodyValue(event.getData() != null ? event.getData().toBytes() : new byte[0])
            .retrieve()
            .toBodilessEntity();
    }

    @Override
    public Mono<ResponseEntity<Void>> publish(final GameEvent gameEvent) {

        log.info("Publishing gameEvent {} to Knative eventing: {}", gameEvent.getEventType(), gameEvent.getGameId());

        return publish("gameEvent", gameEvent.getGameId(), gameEvent.getUuid(), gameEvent);
    }

    private <T> Mono<ResponseEntity<Void>> publish(final String type, final String subject, final UUID uuid, final T data) {
        return publish(createCloudEvent(type, subject, uuid, data));
    }

    <T> CloudEvent createCloudEvent(final String type, final String subject, final UUID uuid, final T data) {
        return CloudEventBuilder.v1()
            .withId(uuid != null ? uuid.toString() : UUID.randomUUID().toString())
            .withType(type)
            .withSource(URI.create(knativeProperties.getSource().trim()))
            .withSubject(subject)
            .withData("application/json", PojoCloudEventData.wrap(data, d -> {
                try {
                    return jsonMapper.writeValueAsBytes(d);
                } catch (final JacksonException e) {
                    throw new RuntimeException(e);
                }
            }))
            .build();
    }
}
