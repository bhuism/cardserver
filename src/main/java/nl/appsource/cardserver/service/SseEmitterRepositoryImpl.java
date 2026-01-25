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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static nl.appsource.cardserver.service.MyServerSentEvent.ping;
import static reactor.core.publisher.Mono.just;
import static reactor.core.publisher.Sinks.EmitFailureHandler.busyLooping;

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

    private final Sinks.Many<@NonNull MyServerSentEvent> mainSink = Sinks.many().multicast().directBestEffort();

    private final Map<String, UserChannel> userChannels = new ConcurrentHashMap<>();

    private static final String HOSTNAME;

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
        log.info("Closing mainSink");

        try {
            final Sinks.EmitResult emitResult = this.mainSink.tryEmitComplete();

            if (emitResult.isFailure()) {
                log.error("mainSink.tryEmitComplete() failure");
            }

        } catch (Throwable t) {
            log.error("", t);
        }
    }

    private Flux<@NonNull MyServerSentEvent> initCache(final String userId) {

        // hello
        final Mono<@NonNull MyServerSentEvent> hello = just(MyServerSentEvent.hello(new HelloEvent().hostName(HOSTNAME)));

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
        final Flux<@NonNull MyServerSentEvent> me = Flux.from(userRepository.findById(userId))
            .map(userToOpenApiConverter::convert)
            .map(MyServerSentEvent::updateUser);

        // online list
        final Mono<@NonNull MyServerSentEvent> onlineList = userRepository.getOnlineFriends(userId)
            .collectList()
            .map(onlineFriends -> MyServerSentEvent.onlineList(new OnlineListEvent().onlineList(onlineFriends)));

        // hello
        final Mono<@NonNull MyServerSentEvent> end = just(MyServerSentEvent.end());

        return Flux.concat(hello, me, friends, games, booms, onlineList, end);

    }

    @PostConstruct
    public void postStruct() {
        Flux.interval(Duration.ofSeconds(5))
            .map(aLong -> ping())
            .subscribe(myServerSentEvent -> {
                mainSink.emitNext(myServerSentEvent, busyLooping(Duration.ofMillis(5000)));
            });
    }

    @Override
    public void send(final String appIdentifier, final String userId, final MyServerSentEvent myServerSentEvent) {
        //mainSink.emitNext(myServerSentEvent, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(5000)));

        if (appIdentifier != null) {

            final UserChannel userChannel = userChannels.get(appIdentifier);

            if (userChannel != null) {
                userChannel.sink.emitNext(myServerSentEvent, busyLooping(Duration.ofMillis(3000)));
            }

        } else if (userId != null) {
            userChannels.values()
                .stream()
                .filter(userChannel -> userChannel.userId.equals(userId))
                .forEach(userChannel -> userChannel.sink.emitNext(myServerSentEvent, busyLooping(Duration.ofMillis(10000))));
        } else {
            log.error("MyServerSentEvent has no appIdentifier or userId, event=" + myServerSentEvent.event());
        }
    }

    @Override
    public Flux<@NonNull ServerSentEvent<@NonNull Object>> subscribe(final String appIdentifier, final String userId, final String remoteAddress, final String userAgent) {

        log.info("{} subscribe() appIdentifier={} userId={}, subscriber={} userChannels={}", remoteAddress, appIdentifier, userId, this.mainSink.currentSubscriberCount(), this.userChannels.size());

        final UserChannel userChannel = userChannels.computeIfAbsent(appIdentifier, id -> new UserChannel(userId));

        new Thread(() -> {
            try {
                Thread.sleep(2000);
                initCache(userId).subscribe(a -> userChannel.sink.emitNext(a, busyLooping(Duration.ofMillis(10000))));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();

        final AtomicLong atomicLong = new AtomicLong(1);

        return sseSessionRepository.deleteById(appIdentifier)
            .onErrorResume(DataRetrievalFailureException.class, e -> Mono.empty())
            .then(just(new SseSession(appIdentifier, remoteAddress, userAgent, HOSTNAME, userId)))
            .flatMap(sseSessionRepository::save)
            .then(sseEventSender.sendOnlineListToFriendsOf(userId))
            .thenMany(
                Flux.concat(just(ping()), userChannel.sink.asFlux(), mainSink.asFlux())
                    .onBackpressureDrop(myServerSentEvent -> {
                        log.info("{} onBackpressureDrop() appIdentifier={} userId={}, subscribers={} userChannels={}, event={}", remoteAddress, appIdentifier, userId, this.mainSink.currentSubscriberCount(), this.userChannels.size(), myServerSentEvent.event());
                    })
                    .doFinally(a -> {
                        log.info("{} doFinally() appIdentifier={} userId={}, subscribers={} userChannels={}", remoteAddress, appIdentifier, userId, this.mainSink.currentSubscriberCount(), this.userChannels.size());
                        userChannels.remove(appIdentifier);
                        sseSessionRepository.deleteById(appIdentifier)
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
        public final Sinks.Many<@NonNull MyServerSentEvent> sink = Sinks.many().multicast().directBestEffort();
        public final String userId;
    }

}
