package nl.appsource.cardserver.stream;

import nl.appsource.cardserver.converters.BoomToOpenApiConverter;
import nl.appsource.cardserver.converters.GameToOpenApiConverter;
import nl.appsource.cardserver.converters.UserToOpenApiConverter;
import nl.appsource.cardserver.couchbase.model.User;
import nl.appsource.cardserver.couchbase.repository.BoomRepository;
import nl.appsource.cardserver.couchbase.repository.GameRepository;
import nl.appsource.cardserver.couchbase.repository.SseSessionRepository;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.stream.service.MyServerSentEvent;
import nl.appsource.cardserver.stream.service.SseEmitterRepository;
import nl.appsource.cardserver.stream.service.SseEmitterRepositoryImpl;
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

    private SseEmitterRepository sseEmitterRepository;

    @BeforeEach
    void setUp() {
        sseEmitterRepository = new SseEmitterRepositoryImpl(
            userRepository,
            gameToOpenApiConverter,
            userToOpenApiConverter,
            boomToOpenApiConverter,
            gameRepository,
            boomRepository,
            sseSessionRepository
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
//        when(sseEventSender.sendOnlineListToFriendsOf(anyString())).thenReturn(Mono.empty());

        Flux<ServerSentEvent<Object>> result = sseEmitterRepository.subscribe("userId", "127.0.0.1", "userAgent");

        StepVerifier.create(result)
            .expectNextMatches(sse -> sse.event().equals("hello"))
            .expectNextMatches(sse -> sse.event().equals("ping"))
            .thenCancel()
            .verify();
    }

    @Test
    void testSendByAppIdentifier() {
        when(userRepository.findById(anyString())).thenReturn(Mono.empty());
        when(userRepository.getFriends(anyString())).thenReturn(Flux.empty());
        when(gameRepository.findGamesByUserId(anyString(), anyInt())).thenReturn(Flux.empty());
        when(boomRepository.findBoomsByUserId(anyString(), anyInt())).thenReturn(Flux.empty());
        when(userRepository.getOnlineFriends(anyString())).thenReturn(Flux.empty());
        when(sseSessionRepository.save(any())).thenReturn(Mono.empty());
        when(sseSessionRepository.deleteById(anyString())).thenReturn(Mono.empty());
//        when(sseEventSender.sendOnlineListToFriendsOf(anyString())).thenReturn(Mono.empty());

        Flux<ServerSentEvent<Object>> result = sseEmitterRepository.subscribe("userId", "127.0.0.1", "userAgent");

        MyServerSentEvent event = new MyServerSentEvent("testEvent", "testData");

        StepVerifier.create(result)
            .expectNextMatches(sse -> sse.event().equals("hello"))
            .expectNextMatches(sse -> sse.event().equals("ping"))
            .then(() -> sseEmitterRepository.sendAppIdentifier("appIdentifier", event))
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
        //when(sseEventSender.sendOnlineListToFriendsOf(anyString())).thenReturn(Mono.empty());

        // Mock some data in initCache
        User user = new User();
        user.setId("userId");
        when(userRepository.findById("userId")).thenReturn(Mono.just(user));

        nl.appsource.generated.openapi.model.User openApiUser = nl.appsource.generated.openapi.model.User.builder().id("userId").build();
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

    @Test
    void testBroadcast() {
        when(userRepository.findById(anyString())).thenReturn(Mono.empty());
        when(userRepository.getFriends(anyString())).thenReturn(Flux.empty());
        when(gameRepository.findGamesByUserId(anyString(), anyInt())).thenReturn(Flux.empty());
        when(boomRepository.findBoomsByUserId(anyString(), anyInt())).thenReturn(Flux.empty());
        when(userRepository.getOnlineFriends(anyString())).thenReturn(Flux.empty());
        when(sseSessionRepository.save(any())).thenReturn(Mono.empty());
        when(sseSessionRepository.deleteById(anyString())).thenReturn(Mono.empty());
        //when(sseEventSender.sendOnlineListToFriendsOf(anyString())).thenReturn(Mono.empty());

        Flux<ServerSentEvent<Object>> result = sseEmitterRepository.subscribe("userId", "127.0.0.1", "userAgent");

        MyServerSentEvent event = new MyServerSentEvent("broadcastEvent", "broadcastData");

        StepVerifier.create(result)
            .expectNextMatches(sse -> sse.event().equals("hello"))
            .expectNextMatches(sse -> sse.event().equals("ping"))
            .then(() -> sseEmitterRepository.sendAppIdentifier(null, event))
            .expectNextMatches(sse -> sse.event().equals("broadcastEvent"))
            .thenCancel()
            .verify();
    }
}
