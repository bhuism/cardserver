package nl.appsource.cardserver.stream.controller;

import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.openapi.service.RedisPubSubService;
import nl.appsource.cardserver.stream.service.SseEmitterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@ActiveProfiles("citest")
@SpringBootTest(properties = "spring.main.web-application-type=reactive", webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
public class SubscribeControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private SseEmitterRepository sseEmitterRepository;

    @MockitoBean
    private RedisPubSubService redisPubSubService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @Autowired
    private org.springframework.context.ApplicationContext context;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient
            .bindToApplicationContext(this.context)
            .apply(org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity())
            .configureClient()
            .build();
    }

    @Test
    void testSubscribe() {
        ServerSentEvent<Object> event = ServerSentEvent.builder().event("hello").build();
        when(sseEmitterRepository.subscribe(anyString(), anyString(), anyString())).thenReturn(Flux.just(event));

        when(userRepository.updateUpdated(anyString())).thenReturn(Mono.just("test-user"));
        User mockUser = new User();
        mockUser.setId("test-user");
        when(userRepository.findById("test-user")).thenReturn(Mono.empty());
        when(userRepository.findBySubject("test-user")).thenReturn(Mono.just(mockUser));

        webTestClient
            .mutateWith(mockJwt()
                .jwt(jwt -> jwt.subject("test-user"))
                .authorities(new SimpleGrantedAuthority("USER"), new SimpleGrantedAuthority("ROLE_USER"))
            )
            .post()
            .uri("/api/v1/subscribe")
            .header("User-Agent", "test-agent")
            .accept(MediaType.TEXT_EVENT_STREAM)
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
