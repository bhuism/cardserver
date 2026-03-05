package nl.appsource.cardserver.stream;

import nl.appsource.cardserver.converters.service.BoomToOpenApiConverter;
import nl.appsource.cardserver.converters.service.GameToOpenApiConverter;
import nl.appsource.cardserver.converters.service.UserToOpenApiConverter;
import nl.appsource.cardserver.couchbase.repository.BoomRepository;
import nl.appsource.cardserver.couchbase.repository.GameRepository;
import nl.appsource.cardserver.couchbase.repository.SseSessionRepository;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.openapi.service.RedisSubscriber;
import nl.appsource.cardserver.stream.service.SseEmitterRepository;
import nl.appsource.cardserver.stream.service.SseEmitterRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Disabled
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

    private JsonMapper jsonMapper = new JsonMapper();

    @MockitoBean
    private RedisSubscriber redisSubscriber;

    @BeforeEach
    void setUp() {
        sseEmitterRepository = new SseEmitterRepositoryImpl(
            redisSubscriber,
            userRepository,
            gameToOpenApiConverter,
            userToOpenApiConverter,
            boomToOpenApiConverter,
            gameRepository,
            boomRepository,
            sseSessionRepository,
            jsonMapper
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

}
