package nl.appsource.cardserver.service;

import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.BoomToOpenApiConverter;
import nl.appsource.cardserver.converter.GameToOpenApiConverter;
import nl.appsource.cardserver.converter.UserToOpenApiConverter;
import nl.appsource.cardserver.model.SseSession;
import nl.appsource.cardserver.repository.BoomRepository;
import nl.appsource.cardserver.repository.GameRepository;
import nl.appsource.cardserver.repository.SseSessionRepository;
import nl.appsource.cardserver.repository.UserRepository;
import org.openapitools.model.HelloEvent;
import org.openapitools.model.OnlineListEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static nl.appsource.cardserver.service.MyServerSentEvent.hello;
import static nl.appsource.cardserver.service.MyServerSentEvent.ping;
import static nl.appsource.cardserver.utils.IDTYPE.SESS;
import static nl.appsource.cardserver.utils.Utils.idGen;
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

    private final SseEventSender sseEventSender;

    private final Sinks.Many<@NonNull MyServerSentEvent> mainSink = Sinks.many().multicast().onBackpressureBuffer(1024, false);

    private final Map<String, UserChannel> userChannels = new ConcurrentHashMap<>();

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

        userChannels.values().forEach(userChannel -> {
            try {
                userChannel.sink.tryEmitComplete();
            } catch (final Throwable t) {
                log.error("Error closing userChannel sink", t);
            }
        });
    }

    private Flux<@NonNull MyServerSentEvent> initCache(final String userId) {

        // users
        final Flux<@NonNull MyServerSentEvent> friends = userRepository.getFriends(userId)
            .map(userToOpenApiConverter::convert)
            .map(MyServerSentEvent::updateUser);

        // games
        final Flux<@NonNull MyServerSentEvent> games = gameRepository.findGamesByUserId(userId, Integer.MAX_VALUE)
            .map(gameToOpenApiConverter::convert)
            .map(MyServerSentEvent::updateGame);

        // forest
        final Flux<@NonNull MyServerSentEvent> booms = boomRepository.findBoomsByUserId(userId, Integer.MAX_VALUE)
            .flatMap(boomToOpenApiConverter::convert)
            .map(MyServerSentEvent::updateBoom);

        // users
        final Flux<@NonNull MyServerSentEvent> me = userRepository.findById(userId)
            .map(userToOpenApiConverter::convert)
            .map(MyServerSentEvent::updateUser)
            .flux();

        // online list
        final Mono<@NonNull MyServerSentEvent> onlineList = userRepository.getOnlineFriends(userId)
            .collectList()
            .map(onlineFriends -> MyServerSentEvent.onlineList(new OnlineListEvent().onlineList(onlineFriends)));

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

    private void tryEmit(final Sinks.Many<MyServerSentEvent> sink, final MyServerSentEvent event, final String context, final boolean disconnectOnOverflow) {
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
    public void send(final String appIdentifier, final String userId, final MyServerSentEvent myServerSentEvent) {
        if (appIdentifier != null) {
            final UserChannel userChannel = userChannels.get(appIdentifier);
            if (userChannel != null) {
                tryEmit(userChannel.sink, myServerSentEvent, "userChannel[" + appIdentifier + "]", true);
            }
        } else if (userId != null) {
            final Set<UserChannel> channels = userChannelsByUserId.get(userId);
            if (channels != null) {
                channels.forEach(userChannel -> tryEmit(userChannel.sink, myServerSentEvent, "userChannel[" + userId + "]", true));
            }
        } else {
            // Broadcast to everyone
            tryEmit(mainSink, myServerSentEvent, "mainSink (broadcast)", false);
        }
    }

    @Override
    public Flux<@NonNull ServerSentEvent<@NonNull Object>> subscribe(final String userId, final String remoteAddress, final String userAgent) {

        final String appIdentifier = idGen(SESS, 8);
        final AtomicLong atomicLong = new AtomicLong(1);

        log.info("{} subscribe() appIdentifier={} userId={}, subscriberCount={} userChannelsCount={}", remoteAddress, appIdentifier, userId, this.mainSink.currentSubscriberCount(), this.userChannels.size());

        final UserChannel userChannel = new UserChannel(userId);
        userChannels.put(appIdentifier, userChannel);
        userChannelsByUserId.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(userChannel);

        final Flux<MyServerSentEvent> helloFlux = Flux.just(hello(new HelloEvent().hostName(HOSTNAME).appIdentifier(appIdentifier)));
        final Flux<MyServerSentEvent> restFlux = Flux.concat(
            Flux.just(ping()),
            Mono.delay(Duration.ofSeconds(1)).thenMany(initCache(userId))
                .doOnComplete(() -> sseEventSender.sendOnlineListToFriendsOf(userId).subscribe())
        );

        final Flux<MyServerSentEvent> liveSinks = Flux.merge(
            userChannel.sink.asFlux().onBackpressureBuffer(2048),
            mainSink.asFlux().onBackpressureBuffer(8192)
        );

        return just(new SseSession(appIdentifier, remoteAddress, userAgent, HOSTNAME))
            .flatMap(sseSessionRepository::save)
            .thenMany(
                Flux.concat(helloFlux, Flux.merge(restFlux, liveSinks))
                    .doFinally(signalType -> {
                        log.info("{} doFinally() signalType={} appIdentifier={} userId={}, subscriberCount={} userChannelsCount={}", remoteAddress, signalType, appIdentifier, userId, this.mainSink.currentSubscriberCount(), this.userChannels.size());

                        userChannels.remove(appIdentifier);
                        userChannelsByUserId.computeIfPresent(userId, (k, channels) -> {
                            channels.remove(userChannel);
                            return channels.isEmpty() ? null : channels;
                        });

                        userChannel.sink.tryEmitComplete();

                        sseSessionRepository.deleteById(appIdentifier)
                            .onErrorComplete(throwable -> throwable instanceof DataRetrievalFailureException)
                            .then(sseEventSender.sendOnlineListToFriendsOf(userId))
                            .subscribe();
                    })
                    .map(myServerSentEvent -> {
                        final ServerSentEvent.Builder<@NonNull Object> builder = ServerSentEvent.builder()
                            .event(myServerSentEvent.event()).id("id:" + atomicLong.getAndIncrement());
                        builder.data(Objects.requireNonNullElse(myServerSentEvent.data(), "{}"));
                        return builder.build();
                    })
            );
    }

    @RequiredArgsConstructor
    private static class UserChannel {
        public final Sinks.Many<@NonNull MyServerSentEvent> sink = Sinks.many().unicast().onBackpressureBuffer(Queues.<MyServerSentEvent>get(1024).get());
        public final String userId;
    }

}
