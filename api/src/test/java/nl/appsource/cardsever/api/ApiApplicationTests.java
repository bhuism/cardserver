package nl.appsource.cardsever.api;

import nl.appsource.cardserver.couchbase.repository.BoomRepository;
import nl.appsource.cardserver.couchbase.repository.FeedbackRepository;
import nl.appsource.cardserver.couchbase.repository.GameRepository;
import nl.appsource.cardserver.couchbase.repository.SseSessionRepository;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.openapi.service.RedisPubSubService;
import nl.appsource.cardserver.openapi.service.RedisStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.couchbase.core.ReactiveCouchbaseTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("citest")
class ApiApplicationTests {

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
    private RedisPubSubService redisPubSubService;

    @MockitoBean
    private ReactiveCouchbaseTemplate reactiveCouchbaseTemplate;

    @MockitoBean
    private RedisStreamService redisStreamService;

    @Test
    void contextLoads() {
    }

}
