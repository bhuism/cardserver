package nl.appsource.cardserver.service;

import nl.appsource.cardserver.converter.BoomToOpenApiConverter;
import nl.appsource.cardserver.converter.GameToOpenApiConverter;
import nl.appsource.cardserver.converter.UserToOpenApiConverter;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.BoomRepository;
import nl.appsource.cardserver.repository.GameRepository;
import nl.appsource.cardserver.repository.SseSessionRepository;
import nl.appsource.cardserver.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SseEmitterRepositoryImplTest {

    @Mock(lenient = true)
    private UserRepository userRepository;
    @Mock(lenient = true)
    private GameToOpenApiConverter gameToOpenApiConverter;
    @Mock(lenient = true)
    private UserToOpenApiConverter userToOpenApiConverter;
    @Mock(lenient = true)
    private BoomToOpenApiConverter boomToOpenApiConverter;
    @Mock(lenient = true)
    private GameRepository gameRepository;
    @Mock(lenient = true)
    private BoomRepository boomRepository;
    @Mock(lenient = true)
    private SseSessionRepository sseSessionRepository;
    @Mock(lenient = true)
    private SseEventSender sseEventSender;

    private SseEmitterRepositoryImpl sseEmitterRepository;

    @BeforeEach
    void setUp() {
        sseEmitterRepository = new SseEmitterRepositoryImpl(
            userRepository,
            gameToOpenApiConverter,
            userToOpenApiConverter,
            boomToOpenApiConverter,
            gameRepository,
            boomRepository,
            sseSessionRepository,
            sseEventSender
        );
    }

    @Test
    void testSubscribe() {
        when(userRepository.findById(anyString())).thenReturn(Mono.empty());
        when(userRepository.getFriends(anyString())).thenReturn(Flux.empty());
        when(gameRepository.findGamesByUserId(anyString(), anyInt())).thenReturn(Flux.empty());
        when(boomRepository.findBoomsByUserId(anyString(), anyInt())).thenReturn(Flux.empty());
        when(userRepository.getOnlineFriends(anyString())).thenReturn(Flux.empty());
        when(sseSessionRepository.save(any())).thenReturn(Mono.empty());
        when(sseSessionRepository.deleteById(anyString())).thenReturn(Mono.empty());
        when(sseEventSender.sendOnlineListToFriendsOf(anyString())).thenReturn(Mono.empty());

        Flux<ServerSentEvent<Object>> result = sseEmitterRepository.subscribe("userId", "127.0.0.1", "userAgent");

        StepVerifier.create(result)
            .expectNextMatches(sse -> sse.event().equals("hello"))
            .expectNextMatches(sse -> sse.event().equals("ping"))
            .thenCancel()
            .verify();
    }

    @Test
    void testSendByUserId() {
        when(userRepository.findById(anyString())).thenReturn(Mono.empty());
        when(userRepository.getFriends(anyString())).thenReturn(Flux.empty());
        when(gameRepository.findGamesByUserId(anyString(), anyInt())).thenReturn(Flux.empty());
        when(boomRepository.findBoomsByUserId(anyString(), anyInt())).thenReturn(Flux.empty());
        when(userRepository.getOnlineFriends(anyString())).thenReturn(Flux.empty());
        when(sseSessionRepository.save(any())).thenReturn(Mono.empty());
        when(sseSessionRepository.deleteById(anyString())).thenReturn(Mono.empty());
        when(sseEventSender.sendOnlineListToFriendsOf(anyString())).thenReturn(Mono.empty());

        Flux<ServerSentEvent<Object>> result = sseEmitterRepository.subscribe("userId", "127.0.0.1", "userAgent");

        MyServerSentEvent event = new MyServerSentEvent("testEvent", "testData");

        StepVerifier.create(result)
            .expectNextMatches(sse -> sse.event().equals("hello"))
            .expectNextMatches(sse -> sse.event().equals("ping"))
            .then(() -> sseEmitterRepository.send(null, "userId", event))
            .expectNextMatches(sse -> sse.event().equals("testEvent"))
            .thenCancel()
            .verify();
    }

    @Test
    void testInitCache() {
        when(userRepository.findById(anyString())).thenReturn(Mono.empty());
        when(userRepository.getFriends(anyString())).thenReturn(Flux.empty());
        when(gameRepository.findGamesByUserId(anyString(), anyInt())).thenReturn(Flux.empty());
        when(boomRepository.findBoomsByUserId(anyString(), anyInt())).thenReturn(Flux.empty());
        when(userRepository.getOnlineFriends(anyString())).thenReturn(Flux.empty());
        when(sseSessionRepository.save(any())).thenReturn(Mono.empty());
        when(sseSessionRepository.deleteById(anyString())).thenReturn(Mono.empty());
        when(sseEventSender.sendOnlineListToFriendsOf(anyString())).thenReturn(Mono.empty());

        // Mock some data in initCache
        User user = new User();
        user.setId("userId");
        when(userRepository.findById("userId")).thenReturn(Mono.just(user));
        org.openapitools.model.User openApiUser = new org.openapitools.model.User().id("userId");
        when(userToOpenApiConverter.convert(user)).thenReturn(openApiUser);

        Flux<ServerSentEvent<Object>> result = sseEmitterRepository.subscribe("userId", "127.0.0.1", "userAgent");

        StepVerifier.create(result)
            .expectNextMatches(sse -> sse.event().equals("hello"))
            .expectNextMatches(sse -> sse.event().equals("ping"))
            // after 1s delay
            .expectNextMatches(sse -> sse.event().equals("updateUser"))
            .thenCancel()
            .verify(Duration.ofSeconds(5));
    }
}
