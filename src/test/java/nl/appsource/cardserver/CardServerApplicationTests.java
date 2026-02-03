package nl.appsource.cardserver;

import nl.appsource.cardserver.repository.BoomRepository;
import nl.appsource.cardserver.repository.FeedbackRepository;
import nl.appsource.cardserver.repository.GameRepository;
import nl.appsource.cardserver.repository.SseEventRepository;
import nl.appsource.cardserver.repository.SseSessionRepository;
import nl.appsource.cardserver.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.couchbase.core.ReactiveCouchbaseTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("citest")
class CardServerApplicationTests {

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
    private SseEventRepository sseEventRepository;

//    @MockitoBean
//    private SingleEventRepository singleEventRepository;

    @MockitoBean
    private ReactiveCouchbaseTemplate reactiveCouchbaseTemplate;

    @Test
    void contextLoads() {
    }

}
