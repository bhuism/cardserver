package nl.appsource.cardserver.gameengine;

import nl.appsource.cardserver.gameengine.service.Worker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("citest")
class GameEngineApplicationTests {

    @MockitoBean
    private Worker worker;

    @Test
    void contextLoads() {
    }

}
