package nl.appsource.cardserver.stream;

import nl.appsource.cardserver.converters.service.BoomToOpenApiConverter;
import nl.appsource.cardserver.converters.service.GameToOpenApiConverter;
import nl.appsource.cardserver.converters.service.UserToOpenApiConverter;
import nl.appsource.cardserver.couchbase.repository.BoomRepository;
import nl.appsource.cardserver.couchbase.repository.GameRepository;
import nl.appsource.cardserver.couchbase.repository.SseSessionRepository;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.openapi.service.SseEventSender;
import nl.appsource.cardserver.stream.service.KafkaEventListener;
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

//@Disabled
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

    @Mock
    private SseEventSender sseEventSender;

    @Mock
    private KafkaEventListener kafkaEventListener;

    @BeforeEach
    void setUp() {
        sseEmitterRepository = new SseEmitterRepositoryImpl(
            userRepository,
            gameToOpenApiConverter,
            boomToOpenApiConverter,
            gameRepository,
            boomRepository,
            kafkaEventListener,
            userToOpenApiConverter
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
        when(kafkaEventListener.kafkaStreams()).thenReturn(Mono.empty());
//        when(sseEventSender.sendOnlineListToFriendsOf(anyString())).thenReturn(Mono.empty());

        Flux<ServerSentEvent<?>> result = sseEmitterRepository.subscribe("userId", "127.0.0.1", "userAgent");

        StepVerifier.create(result)
            .expectNextMatches(sse -> sse.event().equals("hello"))
            .expectNextMatches(sse -> sse.event().equals("ping"))
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
        when(kafkaEventListener.kafkaStreams()).thenReturn(Flux.empty());
        //when(sseEventSender.sendOnlineListToFriendsOf(anyString())).thenReturn(Mono.empty());

        // Mock some data in initCache
        User user = new User();
        user.setId("userId");
        when(userRepository.findById("userId")).thenReturn(Mono.just(user));

        nl.appsource.generated.openapi.model.User openApiUser = new nl.appsource.generated.openapi.model.User().id("userId");
        when(userToOpenApiConverter.convert(user)).thenReturn(openApiUser);

        Flux<ServerSentEvent<?>> result = sseEmitterRepository.subscribe("userId", "127.0.0.1", "userAgent");

        StepVerifier.create(result)
            .expectNextMatches(sse -> sse.event().equals("hello"))
            .expectNextMatches(sse -> sse.event().equals("ping"))
            .expectNextMatches(sse -> sse.event().equals("startCache"))
            .expectNextMatches(sse -> sse.event().equals("updateUser"))
            .expectNextMatches(sse -> sse.event().equals("endCache"))
            .thenCancel()
            .verify(Duration.ofSeconds(5));
    }

}
