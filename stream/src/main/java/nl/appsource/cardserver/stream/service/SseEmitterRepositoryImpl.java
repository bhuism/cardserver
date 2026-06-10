package nl.appsource.cardserver.stream.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converters.service.BoomToOpenApiConverter;
import nl.appsource.cardserver.converters.service.GameToOpenApiConverter;
import nl.appsource.cardserver.converters.service.UserToOpenApiConverter;
import nl.appsource.cardserver.couchbase.repository.BoomRepository;
import nl.appsource.cardserver.couchbase.repository.GameRepository;
import nl.appsource.cardserver.couchbase.repository.SseSessionRepository;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.model.SseSession;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import nl.appsource.cardserver.openapi.service.RedisPubSubService;
import nl.appsource.cardserver.openapi.service.SseEventSender;
import nl.appsource.cardserver.utils.IDTYPE;
import nl.appsource.cardserver.utils.Utils;
import nl.appsource.generated.openapi.model.HelloEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static reactor.core.publisher.Flux.concat;
import static reactor.core.publisher.Flux.merge;
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

    private final RedisPubSubService redisPubSubService;

    private final UserRepository userRepository;

    private final GameToOpenApiConverter gameToOpenApiConverter;

    private final UserToOpenApiConverter userToOpenApiConverter;

    private final BoomToOpenApiConverter boomToOpenApiConverter;

    private final GameRepository gameRepository;

    private final BoomRepository boomRepository;

    private final SseSessionRepository sseSessionRepository;

    private final SseEventSender sseEventSender;

    private final Sinks.Many<MyServerSentEvent> pingSink = Sinks.many().multicast().directBestEffort();

    private static final String HOSTNAME;

    private Disposable heartbeat;

    static {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            host = "unknown";
        }
        HOSTNAME = host;
    }

    @EventListener(ContextClosedEvent.class)
    public void close() {
        log.info("Closing all SSE sinks");

        if (heartbeat != null) {
            heartbeat.dispose();
        }

        try {
            final Sinks.EmitResult emitResult = this.pingSink.tryEmitComplete();
            if (emitResult.isFailure()) {
                log.error("pingSink.tryEmitComplete() failure: {}", emitResult);
            }
        } catch (final Throwable t) {
            log.error("Error closing pingSink", t);
        }

    }

    private Flux<MyServerSentEvent> initCache(final String userId) {

        // users
        final Flux<MyServerSentEvent> friends = userRepository.getFriends(userId)
            .map(userToOpenApiConverter::convert)
            .map(MyServerSentEvent::updateUser);

        // games
        final Flux<MyServerSentEvent> games = gameRepository.findGamesByUserId(userId, Integer.MAX_VALUE)
            .map(gameToOpenApiConverter::convert)
            .map(MyServerSentEvent::updateGame);

        // forest
        final Flux<MyServerSentEvent> booms = boomRepository.findBoomsByUserId(userId, Integer.MAX_VALUE)
            .map(boomToOpenApiConverter::convert)
            .map(MyServerSentEvent::updateBoom);

        // me
        final Flux<MyServerSentEvent> me = userRepository.findById(userId)
            .map(userToOpenApiConverter::convert)
            .map(MyServerSentEvent::updateUser)
            .flux();

        // online list
//        final Mono<MyServerSentEvent> onlineList = userRepository.getOnlineFriends(userId)
//            .collectList()
//            .map(onlineFriends -> MyServerSentEvent.onlineList(new OnlineListEvent().onlineList(onlineFriends)));

        return concat(just(MyServerSentEvent.startCache()), me, friends, games, booms, just(MyServerSentEvent.endCache()));

    }

    @PostConstruct
    public void postConstruct() {
        heartbeat = Flux.interval(Duration.ofSeconds(5))
            .map(SseEmitterRepositoryImpl::ping)
            .subscribe(myServerSentEvent -> pingSink.emitNext(myServerSentEvent, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(1500))));
    }

    @Override
    public Flux<ServerSentEvent<Object>> subscribe(final String userId, final String remoteAddress, final String userAgent) {

        final String appIdentifier = Utils.idGen(IDTYPE.SESS, 8);
        final AtomicLong atomicLong = new AtomicLong(1);

        log.info("{} subscribe() appIdentifier={} userId={}, subscriberCount={}", remoteAddress, appIdentifier, userId, this.pingSink.currentSubscriberCount());

        final Flux<String> friends2 = userRepository.getOnlineFriends(userId).doOnNext(s -> log.debug("Found for user {} online friend: {}", userId, s));
        final Mono<Void> meMono = sseEventSender.sendOnlineListTo(userId, friends2);
        final Mono<Void> friendMono = friends2.flatMap(friendId -> sseEventSender.sendOnlineListTo(friendId, userRepository.getOnlineFriends(friendId).doOnNext(s -> log.debug("Sending friend {} friends: {}", friendId, s)))).then();

        return just(new SseSession(appIdentifier, remoteAddress, userAgent, HOSTNAME))
            .flatMap(sseSessionRepository::save)
            .then(Mono.when(meMono, friendMono))
            .thenMany(
                concat(just(hello(new HelloEvent().hostName(HOSTNAME).appIdentifier(appIdentifier))), just(ping(0)),
                    merge(redisPubSubService.listenTo(userId), redisPubSubService.listenTo(appIdentifier), pingSink.asFlux(), initCache(userId)))
                    .doFinally(signalType -> {
                        log.info("{} doFinally() signalType={} appIdentifier={} userId={}, subscriberCount={}", remoteAddress, signalType, appIdentifier, userId, this.pingSink.currentSubscriberCount());

                        sseSessionRepository.deleteById(appIdentifier)
                            .then(Mono.defer(() -> Mono.when(userRepository.getOnlineFriends(userId).flatMap(friendId -> sseEventSender.sendOnlineListTo(friendId, userRepository.getOnlineFriends(friendId).doOnNext(s -> log.debug("Sending friend {} friends: {}", friendId, s)))))))
//                            .onErrorComplete(throwable -> throwable instanceof DataRetrievalFailureException)
                            .subscribe();

                    })
                    .map(myServerSentEvent -> {
                        final ServerSentEvent.Builder<Object> builder = ServerSentEvent.builder()
                            .event(myServerSentEvent.event()).id("id:" + atomicLong.getAndIncrement());
                        builder.data(Objects.requireNonNullElse(myServerSentEvent.data(), "{}"));
                        return builder.build();
                    })
            );
    }

    public static MyServerSentEvent hello(final HelloEvent helloEvent) {
        return new MyServerSentEvent("hello", helloEvent);
    }

    public static MyServerSentEvent ping(final long count) {
        return new MyServerSentEvent("ping", Map.of("count", count));
    }

}
