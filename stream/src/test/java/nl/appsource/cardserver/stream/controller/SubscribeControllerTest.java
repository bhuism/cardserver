package nl.appsource.cardserver.stream.controller;

import nl.appsource.cardserver.stream.PubSubService;
import nl.appsource.cardserver.stream.service.SseEmitterRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Disabled
@WebFluxTest
public class SubscribeControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private SseEmitterRepository sseEmitterRepository;

    @MockitoBean
    private PubSubService pubSubService;

    @Test
    @WithMockUser(username = "test-user")
    void testSubscribe() {
        ServerSentEvent<Object> event = ServerSentEvent.builder().event("hello").build();
        when(sseEmitterRepository.subscribe(anyString(), anyString(), anyString()))
            .thenReturn(Flux.just(event));

        webTestClient.post()
            .uri("/api/v1/subscribe")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .expectBodyList(ServerSentEvent.class)
            .hasSize(1);
    }

    @Test
    void testSubscribeUnauthorized() {
        webTestClient.post()
            .uri("/api/v1/subscribe")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isUnauthorized();
    }
}
