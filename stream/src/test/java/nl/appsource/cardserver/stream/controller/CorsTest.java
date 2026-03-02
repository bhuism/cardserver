package nl.appsource.cardserver.stream.controller;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.stream.service.SseEmitterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static org.springframework.http.HttpHeaders.ORIGIN;
import static org.springframework.http.HttpMethod.POST;

@Slf4j
@ActiveProfiles("citest")
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
public class CorsTest {

    @LocalServerPort
    private int serverPort;

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private SseEmitterRepository sseEmitterRepository;

    @BeforeEach
    public void setUp() {
        when(sseEmitterRepository.subscribe(any(), any(), any())).thenReturn(Flux.empty());
    }

    @Test
    public void showReturnCorsHeaders() {
        final String origin = "https://www.klaversjassen.nl";

        webTestClient.options()
            .uri("/api/v1/subscribe")
            .header(ORIGIN, origin)
            .header(ACCESS_CONTROL_REQUEST_METHOD, POST.name())
            .header(ACCESS_CONTROL_REQUEST_HEADERS, "content-type")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin)
            .expectHeader().valueMatches(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, POST.name())
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "content-type")
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }

    @Test
    public void showReturnCorsHeadersLocalhost() {
        final String origin = "http://localhost:4280";

        webTestClient.options()
            .uri("/api/v1/subscribe")
            .header(ORIGIN, origin)
            .header(ACCESS_CONTROL_REQUEST_METHOD, POST.name())
            .header(ACCESS_CONTROL_REQUEST_HEADERS, "content-type")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin)
            .expectHeader().valueMatches(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, POST.name())
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "content-type")
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }

}
