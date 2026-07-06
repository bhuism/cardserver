package nl.appsource.cardserver.api;

import nl.appsource.cardserver.api.config.KnativeEventProcessor;
import nl.appsource.cardserver.api.service.GameService;
import nl.appsource.cardserver.api.service.UserService;
import nl.appsource.cardserver.couchbase.repository.BoomRepository;
import nl.appsource.cardserver.couchbase.repository.FeedbackRepository;
import nl.appsource.cardserver.couchbase.repository.GameRepository;
import nl.appsource.cardserver.couchbase.repository.SseSessionRepository;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.openapi.service.RedisPubSubService;
import nl.appsource.cardserver.openapi.service.RedisStreamService;
import nl.appsource.cardserver.openapi.service.SseEventSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.data.couchbase.core.ReactiveCouchbaseTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

@ActiveProfiles("citest")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class KnativeEventProcessorTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private GameService gameService;
    @MockitoBean
    private GameRepository gameRepository;
    @MockitoBean
    private FeedbackRepository feedbackRepository;
    @MockitoBean
    private SseSessionRepository sseSessionRepository;
    @MockitoBean
    private BoomRepository boomRepository;
    @MockitoBean
    private ReactiveCouchbaseTemplate reactiveCouchbaseTemplate;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private RedisPubSubService redisPubSubService;
    @MockitoBean
    private RedisStreamService redisStreamService;
    @MockitoBean
    private SseEventSender sseEventSender;

    @Autowired
    private org.springframework.context.ApplicationContext context;

    @Test
    void testBeanExists() {
        assert context.containsBean("processOrder");
    }

    @Test
    void testProcessOrderExposed() {
        webTestClient
            .post()
            .uri("/processOrder")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void testRootExposed() {
        webTestClient
            .post()
            .uri("/")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isOk();
    }
}
