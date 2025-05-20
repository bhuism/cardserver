package nl.appsource.cardserver;

import nl.appsource.cardserver.repository.GameRepository;
import nl.appsource.cardserver.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("citest")
class CardServerApplicationTests {

    @MockitoBean
    private GameRepository gameRepository;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void contextLoads() {
    }

}
