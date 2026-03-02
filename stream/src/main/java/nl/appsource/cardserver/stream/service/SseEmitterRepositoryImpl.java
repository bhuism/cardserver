package nl.appsource.cardserver.stream.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converters.service.BoomToOpenApiConverter;
import nl.appsource.cardserver.converters.service.GameToOpenApiConverter;
import nl.appsource.cardserver.converters.service.UserToOpenApiConverter;
import nl.appsource.cardserver.couchbase.model.SseSession;
import nl.appsource.cardserver.couchbase.repository.BoomRepository;
import nl.appsource.cardserver.couchbase.repository.GameRepository;
import nl.appsource.cardserver.couchbase.repository.SseSessionRepository;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import nl.appsource.cardserver.utils.IDTYPE;
import nl.appsource.cardserver.utils.Utils;
import nl.appsource.generated.openapi.model.HelloEvent;
import nl.appsource.generated.openapi.model.OnlineListEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataRetrievalFailureException;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static nl.appsource.cardserver.openapi.MyServerSentEvent.hello;
import static nl.appsource.cardserver.openapi.MyServerSentEvent.ping;
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

    private final UserToOpenApiConverter userToOpenApiConverter;

    private final BoomToOpenApiConverter boomToOpenApiConverter;

    private final GameRepository gameRepository;

    private final BoomRepository boomRepository;

    private final SseSessionRepository sseSessionRepository;

    private final Sinks.Many<nl.appsource.cardserver.openapi.MyServerSentEvent> mainSink = Sinks.many().multicast().onBackpressureBuffer(1024, false);

    private final Map<String, UserChannel> userChannelsByApplicationId = new ConcurrentHashMap<>();

    private final Map<String, Set<UserChannel>> userChannelsByUserId = new ConcurrentHashMap<>();

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
            final Sinks.EmitResult emitResult = this.mainSink.tryEmitComplete();
            if (emitResult.isFailure()) {
                log.error("mainSink.tryEmitComplete() failure: {}", emitResult);
            }
        } catch (final Throwable t) {
            log.error("Error closing mainSink", t);
        }

        userChannelsByApplicationId.values().forEach(userChannel -> {
            try {
                userChannel.sink.tryEmitComplete();
            } catch (final Throwable t) {
                log.error("Error closing userChannel sink", t);
            }
        });
    }

    private Flux<nl.appsource.cardserver.openapi.MyServerSentEvent> initCache(final String userId) {

        // users
        final Flux<nl.appsource.cardserver.openapi.MyServerSentEvent> friends = userRepository.getFriends(userId)
            .map(userToOpenApiConverter::convert)
            .map(nl.appsource.cardserver.openapi.MyServerSentEvent::updateUser);

        // games
        final Flux<nl.appsource.cardserver.openapi.MyServerSentEvent> games = gameRepository.findGamesByUserId(userId, Integer.MAX_VALUE)
            .map(gameToOpenApiConverter::convert)
            .map(nl.appsource.cardserver.openapi.MyServerSentEvent::updateGame);

        // forest
        final Flux<nl.appsource.cardserver.openapi.MyServerSentEvent> booms = boomRepository.findBoomsByUserId(userId, Integer.MAX_VALUE)
            .map(boomToOpenApiConverter::convert)
            .map(nl.appsource.cardserver.openapi.MyServerSentEvent::updateBoom);

        // users
        final Flux<nl.appsource.cardserver.openapi.MyServerSentEvent> me = userRepository.findById(userId)
            .map(userToOpenApiConverter::convert)
            .map(nl.appsource.cardserver.openapi.MyServerSentEvent::updateUser)
            .flux();

        // online list
        final Mono<nl.appsource.cardserver.openapi.MyServerSentEvent> onlineList = userRepository.getOnlineFriends(userId)
            .collectList()
            .map(onlineFriends -> nl.appsource.cardserver.openapi.MyServerSentEvent.onlineList(OnlineListEvent.builder().onlineList(onlineFriends).build()));

        return Flux.concat(me, friends, games, booms, onlineList);

    }

    @PostConstruct
    public void postConstruct() {
        heartbeat = Flux.interval(Duration.ofSeconds(5))
            .map(aLong -> ping())
            .subscribe(myServerSentEvent -> {
                tryEmit(mainSink, myServerSentEvent, "mainSink", false);
            });
    }

    private void tryEmit(final Sinks.Many<nl.appsource.cardserver.openapi.MyServerSentEvent> sink, final nl.appsource.cardserver.openapi.MyServerSentEvent event, final String context, final boolean disconnectOnOverflow) {
        final Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            if (result == Sinks.EmitResult.FAIL_OVERFLOW) {
                log.warn("{} overflow, event: {}", context, event.event());
                if (disconnectOnOverflow) {
                    sink.tryEmitError(new RuntimeException("Buffer overflow"));
                }
            } else if (result == Sinks.EmitResult.FAIL_TERMINATED || result == Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
                log.debug("{} sink terminated or no subscribers", context);
            } else {
                log.warn("{} emit failed: {}", context, result);
            }
        }
    }

    @Override
    public void sendAppIdentifier(final String appIdentifier, final nl.appsource.cardserver.openapi.MyServerSentEvent myServerSentEvent) {

        Optional.ofNullable(userChannelsByApplicationId.get(appIdentifier))
            .ifPresent(userChannel -> tryEmit(userChannel.sink, myServerSentEvent, "userChannel[" + appIdentifier + "]", true));

        final UserChannel userChannel = userChannelsByApplicationId.get(appIdentifier);

        if (userChannel != null) {
            tryEmit(userChannel.sink, myServerSentEvent, "userChannel[" + appIdentifier + "]", true);
        }
//        } else if (userId != null) {
//            final Set<UserChannel> channels = userChannelsByUserId.get(userId);
//            if (channels != null) {
//                channels.forEach(userChannel -> tryEmit(userChannel.sink, myServerSentEvent, "userChannel[" + userId + "]", true));
//            }
//        }
    }

    @Override
    public Flux<ServerSentEvent<Object>> subscribe(final String userId, final String remoteAddress, final String userAgent) {

        final String appIdentifier = Utils.idGen(IDTYPE.SESS, 8);
        final AtomicLong atomicLong = new AtomicLong(1);

        log.info("{} subscribe() appIdentifier={} userId={}, subscriberCount={} userChannelsCount={}", remoteAddress, appIdentifier, userId, this.mainSink.currentSubscriberCount(), this.userChannelsByApplicationId.size());

        final UserChannel userChannel = new UserChannel(userId);

        userChannelsByApplicationId.put(appIdentifier, userChannel);
        userChannelsByUserId.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(userChannel);

        final Flux<MyServerSentEvent> helloFlux = Flux.just(hello(HelloEvent.builder().hostName(HOSTNAME).appIdentifier(appIdentifier).build()));

        final Flux<nl.appsource.cardserver.openapi.MyServerSentEvent> restFlux =
            helloFlux.then(Mono.delay(Duration.ofSeconds(1)))
                .then(Mono.just(ping()))
                .thenMany(initCache(userId)
                .thenMany(Flux.merge(mainSink.asFlux(), userChannel.sink.asFlux()))
            );

        return just(new SseSession(appIdentifier, remoteAddress, userAgent, HOSTNAME))
            .flatMap(sseSessionRepository::save)
            .thenMany(
                restFlux
                    .doFinally(signalType -> {
                        log.info("{} doFinally() signalType={} appIdentifier={} userId={}, subscriberCount={} userChannelsCount={}", remoteAddress, signalType, appIdentifier, userId, this.mainSink.currentSubscriberCount(), this.userChannelsByApplicationId.size());

                        userChannelsByApplicationId.remove(appIdentifier);
                        userChannelsByUserId.computeIfPresent(userId, (k, channels) -> {
                            channels.remove(userChannel);
                            return channels.isEmpty() ? null : channels;
                        });

                        sseSessionRepository.deleteById(appIdentifier)
                            .onErrorComplete(throwable -> throwable instanceof DataRetrievalFailureException)
                            //.then(sseEventSender.sendOnlineListToFriendsOf(userId))
                            .subscribe();

                        userChannel.sink.tryEmitComplete();

                    })
                    .map(myServerSentEvent -> {
                        final ServerSentEvent.Builder<Object> builder = ServerSentEvent.builder()
                            .event(myServerSentEvent.event()).id("id:" + atomicLong.getAndIncrement());
                        builder.data(Objects.requireNonNullElse(myServerSentEvent.data(), "{}"));
                        return builder.build();
                    })
            );
    }

    @RequiredArgsConstructor
    private static class UserChannel {
        public final Sinks.Many<nl.appsource.cardserver.openapi.MyServerSentEvent> sink = Sinks.many().unicast().onBackpressureBuffer();
        public final String userId;
    }

}
