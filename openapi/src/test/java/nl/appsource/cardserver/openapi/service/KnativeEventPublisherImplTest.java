package nl.appsource.cardserver.openapi.service;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import nl.appsource.cardserver.openapi.config.KnativeProperties;
import nl.appsource.generated.openapi.model.GameEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class KnativeEventPublisherImplTest {

    private KnativeEventPublisherImpl knativeEventPublisher;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private KnativeProperties knativeProperties;

    @BeforeEach
    void setUp() {
        knativeProperties = new KnativeProperties();
        knativeProperties.setSink("http://localhost:8080");
        knativeProperties.setSource("test-source");

        knativeEventPublisher = new KnativeEventPublisherImpl(WebClient.builder(), jsonMapper, knativeProperties);
    }

    @Test
    void shouldCreateCloudEventWithCorrectAttributes() {
        final GameEvent gameEvent = new GameEvent();
        gameEvent.setEventType(GameEvent.EventTypeEnum.PLAY_CARD);
        gameEvent.setGameId("game-123");
        final UUID uuid = UUID.randomUUID();
        gameEvent.setUuid(uuid);

        final CloudEvent event = knativeEventPublisher.createCloudEvent("gameEvent", gameEvent.getGameId(), gameEvent.getUuid(), gameEvent);

        assertThat(event.getSpecVersion()).isEqualTo(SpecVersion.V1);
        assertThat(event.getType()).isEqualTo("gameEvent");
        assertThat(event.getSource().toString()).isEqualTo("test-source");
        assertThat(event.getId()).isEqualTo(uuid.toString());
        assertThat(event.getSubject()).isEqualTo("game-123");
        assertThat(event.getDataContentType()).isEqualTo("application/json");
    }

    @Test
    void shouldCreateCloudEventWithRandomIdWhenUuidIsNull() {
        final GameEvent gameEvent = new GameEvent();
        gameEvent.setEventType(GameEvent.EventTypeEnum.PLAY_CARD);
        gameEvent.setGameId("game-123");
        gameEvent.setUuid(null);

        final CloudEvent event = knativeEventPublisher.createCloudEvent("gameEvent", gameEvent.getGameId(), gameEvent.getUuid(), gameEvent);

        assertThat(event.getId()).isNotNull();
        assertThat(UUID.fromString(event.getId())).isNotNull();
    }

    @Test
    void shouldPublishToCorrectSink() {
        final ExchangeFunction exchangeFunction = mock(ExchangeFunction.class);
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(ClientResponse.create(HttpStatus.OK).build()));

        final WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);
        final KnativeEventPublisherImpl publisher = new KnativeEventPublisherImpl(builder, jsonMapper, knativeProperties);

        final GameEvent gameEvent = new GameEvent();
        gameEvent.setEventType(GameEvent.EventTypeEnum.PLAY_CARD);
        gameEvent.setGameId("game-123");

        publisher.publish(gameEvent).block();

        final ArgumentCaptor<ClientRequest> requestCaptor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(exchangeFunction).exchange(requestCaptor.capture());

        assertThat(requestCaptor.getValue().url()).hasToString("http://localhost:8080");
        assertThat(requestCaptor.getValue().method().name()).isEqualTo("POST");
        assertThat(requestCaptor.getValue().headers().getFirst("ce-type")).isEqualTo("gameEvent");
        assertThat(requestCaptor.getValue().headers().getFirst("ce-source")).isEqualTo("test-source");
        assertThat(requestCaptor.getValue().headers().getFirst("ce-specversion")).isEqualTo("1.0");
        assertThat(requestCaptor.getValue().headers().getContentType().toString()).isEqualTo("application/json");
    }
}
