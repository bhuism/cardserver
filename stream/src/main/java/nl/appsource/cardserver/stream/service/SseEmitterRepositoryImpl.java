package nl.appsource.cardserver.stream.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converters.service.BoomToOpenApiConverter;
import nl.appsource.cardserver.converters.service.GameToOpenApiConverter;
import nl.appsource.cardserver.converters.service.UserToOpenApiConverter;
import nl.appsource.cardserver.couchbase.repository.BoomRepository;
import nl.appsource.cardserver.couchbase.repository.GameRepository;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import nl.appsource.cardserver.utils.IDTYPE;
import nl.appsource.cardserver.utils.Utils;
import nl.appsource.generated.openapi.model.HelloEvent;
import nl.appsource.generated.openapi.model.User;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Collections.emptySet;
import static reactor.core.publisher.Flux.concat;
import static reactor.core.publisher.Mono.just;

/**
 * The type Sse emitter repository.
 *
 * @see <a href="https://www.baeldung.com/spring-server-sent-events">Spring Server-Sent Events</a>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SseEmitterRepositoryImpl implements SseEmitterRepository {

    private final UserRepository userRepository;

    private final GameToOpenApiConverter gameToOpenApiConverter;

    private final BoomToOpenApiConverter boomToOpenApiConverter;

    private final GameRepository gameRepository;

    private final BoomRepository boomRepository;

    private static final String HOSTNAME;

    private final KafkaEventListener kafkaEventListener;

    private final UserToOpenApiConverter userToOpenApiConverter;

    static {
        String host;
        try {
            host = InetAddress.getLocalHost()
                .getHostName();
        } catch (UnknownHostException e) {
            host = "unknown";
        }
        HOSTNAME = host;
    }

    private Flux<MyServerSentEvent<?>> initCache(final String userId) {

        // users
        final Flux<MyServerSentEvent<?>> friends = userRepository.getFriends(userId)
            .map(userToOpenApiConverter::convert)
            .map((User user) -> MyServerSentEvent.updateUser(user, Set.of(userId)))
            .<MyServerSentEvent<?>>map(it -> it);

        // games
        final Flux<MyServerSentEvent<?>> games = gameRepository.findGamesByUserId(userId, Integer.MAX_VALUE)
            .map(gameToOpenApiConverter::convert)
            .map(MyServerSentEvent::updateGame)
            .<MyServerSentEvent<?>>map(it -> it);

        // forest
        final Flux<MyServerSentEvent<?>> booms = boomRepository.findBoomsByUserId(userId, Integer.MAX_VALUE)
            .map(boomToOpenApiConverter::convert)
            .map(MyServerSentEvent::updateBoom)
            .<MyServerSentEvent<?>>map(it -> it);


        final Flux<MyServerSentEvent<?>> me = userRepository.findById(userId)
            .map(userToOpenApiConverter::convert)
            .map((User user) -> MyServerSentEvent.updateUser(user, Set.of(userId)))
            .<MyServerSentEvent<?>>map(it -> it)
            .flux();

        return concat(
            just(MyServerSentEvent.startCache(userId)),
            friends, games, booms, me,
            just(MyServerSentEvent.endCache(userId))
        );

    }

    @Override
    public Flux<ServerSentEvent<?>> subscribe(final String userId, final String remoteAddress, final String userAgent) {

        final String appIdentifier = Utils.idGen(IDTYPE.SESS, 8);
        final AtomicLong atomicLong = new AtomicLong(1);

        log.info("{} subscribe() appIdentifier={} userId={}", remoteAddress, appIdentifier, userId);

//        final Flux<String> friends1 = userRepository.getOnlineFriends(userId);

        // final Mono<MyServerSentEvent<?>> onlineListSse = friends1.collectList().map(friends -> onlineList(new OnlineListEvent().onlineList(friends)));

//        final Flux<String> friends3 = userRepository.getOnlineFriends(userId);

//        final Mono<Void> friendsMono = friends3.flatMap(friendId -> sseEventSender.sendOnlineListTo(friendId, Flux.merge(userRepository.getOnlineFriends(friendId), just(userId)).distinct().doOnNext(s -> log.debug("Sending friend {} friends: {}", friendId, s)))).then();

//        return just(new SseSession(appIdentifier, remoteAddress, userAgent, HOSTNAME))
//            .flatMap(sseSessionRepository::save)


        final Flux<MyServerSentEvent<?>> pingSink = Flux.interval(Duration.ofSeconds(5))
            .map(count -> ping(count + 1));

        final Flux<MyServerSentEvent<?>> asyncCache = Flux.defer(() -> initCache(userId))
            .subscribeOn(Schedulers.boundedElastic());

        return //friendsMono
//            .thenMany(
            concat(just(hello(appIdentifier)), just(ping(0)), Flux.merge(pingSink, asyncCache, kafkaEventListener.kafkaStreams()))
                .onBackpressureBuffer(1024, BufferOverflowStrategy.DROP_OLDEST)
                .doFinally(signalType -> {
                    log.info("{} doFinally() signalType={} appIdentifier={} userId={}", remoteAddress, signalType, appIdentifier, userId);
//
//                        sseSessionRepository.deleteById(appIdentifier)
//                            .then(Mono.defer(() -> Mono.when(userRepository.getOnlineFriends(userId).flatMap(friendId -> sseEventSender.sendOnlineListTo(friendId, userRepository.getOnlineFriends(friendId).doOnNext(s -> log.debug("Sending friend {} friends: {}", friendId, s)))))))
//                            .onErrorComplete(_ -> true)
//                            .subscribe();

                })
                .filter(myServerSentEvent -> myServerSentEvent.userIds()
                    .contains(userId) || myServerSentEvent.userIds()
                    .isEmpty())
                .map(myServerSentEvent -> {
                    final ServerSentEvent.Builder<Object> builder = ServerSentEvent.builder()
                        .event(myServerSentEvent.event())
                        .id("id:" + atomicLong.getAndIncrement());
                    builder.data(Objects.requireNonNullElse(myServerSentEvent.data(), "{}"));
                    return builder.build();
                });
        //          );
    }

    public static MyServerSentEvent<?> hello(final String appIdentifier) {
        return new MyServerSentEvent<>("hello", new HelloEvent().hostName(HOSTNAME)
            .appIdentifier(appIdentifier), emptySet());
    }

    public static MyServerSentEvent<?> ping(final long count) {
        return new MyServerSentEvent<>("ping", Map.of("count", count), emptySet());
    }

}
