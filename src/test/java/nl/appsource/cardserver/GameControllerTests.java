package nl.appsource.cardserver;

import nl.appsource.cardserver.controller.BoomController;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.repository.GameRepository;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.BoomService;
import nl.appsource.cardserver.service.GameService;
import nl.appsource.cardserver.service.SseEmitterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;

@ActiveProfiles("citest")
@SpringBootTest(properties = "spring.main.web-application-type=reactive", webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class GameControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SseEmitterRepository sseEmitterRepository;

    @MockitoBean
    private GameService gameService;

    @MockitoBean
    private BoomService boomService;

    @MockitoBean
    private BoomController boomController;

    @MockitoBean
    private GameRepository gameRepository;

    @Test
    @WithMockUser(username = "user-abc")
    void getGame_whenGameNotFound_shouldReturnNotFound() {

        when(gameService.getGame("user-abc", "game-123")).thenReturn(Mono.empty());
        when(sseEmitterRepository.validate(UUID.fromString("0ff9e5c0-da5e-48e1-a3ae-e5a93880ed90"), "user-abc")).thenReturn(true);

        webTestClient
            // Set up a mock authenticated user for the request
//            .mutateWith(mockAuthentication(new UsernamePasswordAuthenticationToken("user-abc", "password", Collections.singletonList(new SimpleGrantedAuthority("USER")))))
            .get()
            .uri("/api/v1/games/game-123")
            .header("App-Identifier", "0ff9e5c0-da5e-48e1-a3ae-e5a93880ed90")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isNotFound(); // Expecting 404 Not Found because the controller now explicitly returns it
    }

    @Test
    @WithMockUser(username = "user-abc")
    void getGame_whenGameFound_shouldReturnGame() {

        final Game mockGame = new Game();
        mockGame.setId("myid");
        mockGame.setPlayerCard(Map.of(Card.As, 0));
        mockGame.setTurns(Collections.emptyList());
        mockGame.setPlayers(List.of("a", "b", "c", "d"));
        mockGame.setDealer(0);
        mockGame.setSay(new HashMap<>());

//        final org.openapitools.model.Game expectedGame = new org.openapitools.model.Game();
//        expectedGame.setId("myid");
//        expectedGame.setPlayerCard(List.of(new GamePlayerCardInner(org.openapitools.model.Card.AS, 0)));
//        expectedGame.setTurns(Collections.emptyList());
//        expectedGame.setPlayers(List.of("a", "b", "c", "d"));
//        expectedGame.setDealer(0);
//        expectedGame.setWhoSay(Optional.of(1));


        when(gameService.getGame("user-abc", "game-123")).thenReturn(Mono.just(mockGame));
        when(sseEmitterRepository.validate(UUID.fromString("0ff9e5c0-da5e-48e1-a3ae-e5a93880ed90"), "user-abc")).thenReturn(true);

        webTestClient
            // Set up a mock authenticated user for the request
//            .mutateWith(mockAuthentication(new UsernamePasswordAuthenticationToken("user-abc", "password", Collections.singletonList(new SimpleGrantedAuthority("USER")))))
            .get()
            .uri("/api/v1/games/game-123")
            .header("App-Identifier", "0ff9e5c0-da5e-48e1-a3ae-e5a93880ed90")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(org.openapitools.model.Game.class);
//            .isEqualTo(expectedGame);
    }

}