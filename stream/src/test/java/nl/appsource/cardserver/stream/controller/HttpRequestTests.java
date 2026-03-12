package nl.appsource.cardserver.stream.controller;

import nl.appsource.cardserver.couchbase.repository.BoomRepository;
import nl.appsource.cardserver.couchbase.repository.FeedbackRepository;
import nl.appsource.cardserver.couchbase.repository.GameRepository;
import nl.appsource.cardserver.couchbase.repository.SseSessionRepository;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.openapi.service.RedisPubSubService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("citest")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class HttpRequestTests {

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private GameRepository gameRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private BoomRepository boomRepository;

    @MockitoBean
    private FeedbackRepository feedbackRepository;

    @MockitoBean
    private SseSessionRepository sseSessionRepository;

    @MockitoBean
    public RedisPubSubService redisPubSubService;

//    @Test
//    void greetingShouldReturnDefaultMessage() {
//        assertThat(this.webTestClient.get().uri("http://localhost:" + port + "/", String.class).exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody())
//            .contains("logo192.png");
//    }

    @Test
    public void actuatorHealthShouldReturnDefaultMessage() {
        assertThat(this.webTestClient.get().uri("http://localhost:" + managementPort + "/manage/health", String.class).exchange().expectStatus().isOk());
    }

    @Test
    public void actuatorHealthLiveNessShouldReturnDefaultMessage() {
        assertThat(this.webTestClient.get().uri("http://localhost:" + managementPort + "/manage/health/liveness", String.class).exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody())
            .isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    public void actuatorHealthReadinessShouldReturnDefaultMessage() {
        assertThat(this.webTestClient.get().uri("http://localhost:" + managementPort + "/manage/health/readiness", String.class).exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody())
            .isEqualTo("{\"status\":\"UP\"}");
    }
}