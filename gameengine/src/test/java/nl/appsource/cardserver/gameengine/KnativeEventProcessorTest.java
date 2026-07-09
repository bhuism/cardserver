package nl.appsource.cardserver.gameengine;

import nl.appsource.cardserver.couchbase.repository.BoomRepository;
import nl.appsource.cardserver.couchbase.repository.FeedbackRepository;
import nl.appsource.cardserver.couchbase.repository.GameRepository;
import nl.appsource.cardserver.couchbase.repository.SseSessionRepository;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.openapi.service.RedisPubSubService;
import nl.appsource.cardserver.openapi.service.RedisStreamService;
import nl.appsource.cardserver.openapi.service.SseEventSender;
import nl.appsource.generated.openapi.model.GameEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.data.couchbase.core.ReactiveCouchbaseTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@ActiveProfiles("citest")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class KnativeEventProcessorTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private UserRepository userRepository;
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
    private RedisPubSubService redisPubSubService;
    @MockitoBean
    private RedisStreamService redisStreamService;
    @MockitoBean
    private SseEventSender sseEventSender;

    @Test
    void testProcessOrderExposed() {

        final GameEvent gameEvent = new GameEvent(GameEvent.EventTypeEnum.PLAY_CARD, "1234", "gameId", 0L);

        webTestClient
            .post()
            .uri("/gameEvent")
            .header("ce-id", "1234")
            .header("ce-specversion", "1.0")
            .header("ce-source", "http://my-source")
            .header("ce-type", "test")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(gameEvent)
            .exchange()
            .expectStatus().isOk();
    }
}