package nl.appsource.cardserver.api;

import nl.appsource.cardserver.api.controller.BoomController;
import nl.appsource.cardserver.api.service.BoomService;
import nl.appsource.cardserver.api.service.GameService;
import nl.appsource.cardserver.api.service.UserService;
import nl.appsource.cardserver.converters.config.ConvertersConfig;
import nl.appsource.cardserver.couchbase.repository.BoomRepository;
import nl.appsource.cardserver.couchbase.repository.FeedbackRepository;
import nl.appsource.cardserver.couchbase.repository.GameRepository;
import nl.appsource.cardserver.couchbase.repository.SseSessionRepository;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.GameVariant;
import nl.appsource.cardserver.model.Suit;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.openapi.service.RedisPubSubService;
import nl.appsource.cardserver.openapi.service.RedisStreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.data.couchbase.core.ReactiveCouchbaseTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@ActiveProfiles("citest")
@SpringBootTest(properties = "spring.main.web-application-type=reactive", webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
@Import(ConvertersConfig.class)
public class GameControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private GameService gameService;

    @MockitoBean
    private BoomService boomService;

    @MockitoBean
    private BoomController boomController;

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

    @Autowired
    private org.springframework.context.ApplicationContext context;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient
            .bindToApplicationContext(this.context)
            .apply(org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity())
            .configureClient()
            .build();
        when(gameService.getGame("user-abc", "game-notfound")).thenReturn(Mono.empty());

        final Game mockGame = new Game();
        mockGame.setId("game-found");
        mockGame.setPlayerCard(Map.of(Card.As, 0));
        mockGame.setTurns(Collections.emptyList());
        mockGame.setPlayers(List.of("a", "b", "c", "d"));
        mockGame.setDealer(0);
        mockGame.setSay(new HashMap<>());
        mockGame.setTrump(Suit.Spades);
        mockGame.setGameVariant(GameVariant.ROTTERDAMS);

        when(gameService.getGame("user-abc", "game-found")).thenReturn(Mono.just(mockGame));

        final User user = new User();
        user.setId("user-abc");

        when(userRepository.findById("user-abc")).thenReturn(Mono.just(user));
        when(userRepository.save(user)).thenReturn(Mono.just(user));
        when(userRepository.updateUpdated("user-abc")).thenReturn(Mono.just("user-abc"));
        when(sseSessionRepository.updateUpdated("sess0ff9e5c0-da5e-48e1-a3ae-e5a93880ed90")).thenReturn(Mono.just("sess0ff9e5c0-da5e-48e1-a3ae-e5a93880ed90"));
    }


    @Test
    void getGame_whenGameNotFound_shouldReturnNotFound() {

        webTestClient
            .mutateWith(mockJwt()
                .jwt(jwt -> jwt.subject("user-abc"))
                .authorities(new SimpleGrantedAuthority("USER"), new SimpleGrantedAuthority("ROLE_USER"))
            )
            .get()
            .uri("/api/v1/games/game-notfound")
            .header("App-Identifier", "sess0ff9e5c0-da5e-48e1-a3ae-e5a93880ed90")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isNotFound(); // Expecting 404 Not Found because the controller now explicitly returns it
    }

    @Test
    void getGame_whenGameFound_shouldReturnGame() {

        webTestClient
            .mutateWith(mockJwt()
                .jwt(jwt -> jwt.subject("user-abc"))
                .authorities(new SimpleGrantedAuthority("USER"), new SimpleGrantedAuthority("ROLE_USER"))
            )
            .get()
            .uri("/api/v1/games/game-found")
            .header("App-Identifier", "sess0ff9e5c0-da5e-48e1-a3ae-e5a93880ed90")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(nl.appsource.generated.openapi.model.Game.class);
//            .isEqualTo(expectedGame);
    }

}
