package nl.appsource.cardserver;

import nl.appsource.cardserver.repository.GameRepository;
import nl.appsource.cardserver.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("citest")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HttpRequestTests {

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private GameRepository gameRepository;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void greetingShouldReturnDefaultMessage() {
        assertThat(this.restTemplate.getForObject("http://localhost:" + port + "/", String.class)).contains("star.png");
    }

    @Test
    public void actuatorHealthShouldReturnDefaultMessage() {
        assertThat(this.restTemplate.getForObject("http://localhost:" + managementPort + "/manage/health", String.class)).isEqualTo("{\"status\":\"UP\",\"groups\":[\"liveness\",\"readiness\"]}");
    }

    @Test
    public void actuatorHealthLiveNessShouldReturnDefaultMessage() {
        assertThat(this.restTemplate.getForObject("http://localhost:" + managementPort + "/manage/health/liveness", String.class)).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    public void actuatorHealthReadinessShouldReturnDefaultMessage() {
        assertThat(this.restTemplate.getForObject("http://localhost:" + managementPort + "/manage/health/readiness", String.class)).isEqualTo("{\"status\":\"UP\"}");
    }
}