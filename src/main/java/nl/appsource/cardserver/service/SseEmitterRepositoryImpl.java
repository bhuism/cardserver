package nl.appsource.cardserver.service;

import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.BoomToOpenApiConverter;
import nl.appsource.cardserver.converter.GameToOpenApiConverter;
import nl.appsource.cardserver.converter.UserToOpenApiConverter;
import nl.appsource.cardserver.model.Boom;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.SseSession;
import nl.appsource.cardserver.model.User;
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

    private final SseEventSender sseEventSender;

//    private Flux<@NonNull String> getFriends(final String userId) {
//        return userRepository.findById(userId)
//            .map(User::getInvites)
//            .flatMapMany(list -> userRepository.findIncomingInvites(userId)
//                .filter(list::contains));
//    }

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

    private Flux<@NonNull User> getFriends(final String userId) {
        return userRepository.getFriends(userId);
    }

    private Flux<@NonNull String> getOnlineFriends(final String userId) {
        return userRepository.getOnlineFriends(userId);
    }

    @Override
    public Mono<Void> sendOnlineListToFriendsOf(final String userId) {
        return getOnlineFriends(userId).flatMap(this::sendOnlineListTo).then();
    }

    @Override
    public Mono<Void> sendOnlineListTo(final String userId) {
        return getOnlineFriends(userId)
            .collectList()
            .flatMap(onlineList -> sseEventSender.sendOnlineListTo(userId, onlineList));
    }

//    @Override
//    public void sendMessage(final Collection<String> userIds, final UserMessage userMessage) {
//        userIds.forEach(userId -> send(MySseEmitter.createMessageEvent(null, userId, userMessage)));

    /// /        doSelectedUserIds(userIds, mySseEmitter -> mySseEmitter.message(userMessage));
//    }
    @Override
    public void updateGame(final Game game) {
        final org.openapitools.model.Game convertedGame = gameToOpenApiConverter.convert(game);
        game.getPlayers().forEach(player -> send(null, player, MyServerSentEvent.updateGame(convertedGame)));
    }

//    @Override
//    public void updateGameForId(final String appIdentifier, final Game game) {

    /// /        doId(appIdentifier, mySseEmitter -> mySseEmitter.sendUpdateGame(requireNonNull(gameToOpenApiConverter.convert(game))));
//        send(createServerSentEvent(appIdentifier, null, gameToOpenApiConverter.convert(game)));
//    }
    @Override
    public void updateUser(final User user) {
        final org.openapitools.model.User convertedUser = userToOpenApiConverter.convert(user);
        getOnlineFriends(user.getId()).mergeWith(Mono.just(user.getId())).subscribe(friend -> send(null, friend, MyServerSentEvent.updateUser(convertedUser)));
    }

    @Override
    public void updateBoom(final Boom boom) {
//        doSelectedUserIds(boom.getPlayers(), mySseEmitter -> mySseEmitter.sendupdateBoom(boomToOpenApiConverter.convert(boom)));

        final org.openapitools.model.Boom convertedBoom = boomToOpenApiConverter.convert(boom);
//        convertedBoom.getPlayers().forEach(player -> send(null, player, createServerSentEvent(convertedBoom)));

        Flux.fromIterable(boom.getPlayers())
            .concatWith(Flux.just(boom.getCreator()))
            .distinct()
            .subscribe(player -> send(null, player, MyServerSentEvent.updateBoom(convertedBoom)));

    }

    @Override
    public Mono<Game> newGame(final Game game) {
        return Flux.fromIterable(game.getPlayers())
            .filter(player -> !player.equals(game.getCreator()))
            .flatMap(player -> sseEventSender.newGame(player, game))
            .then(Mono.just(game));
    }


    private Flux<@NonNull MyServerSentEvent> initCache(final String appIdentifier, final String userId) {

        final Mono<@NonNull MyServerSentEvent> ping = Mono.just(MyServerSentEvent.ping());

        // hello
        final Mono<@NonNull MyServerSentEvent> hello = Mono.just(MyServerSentEvent.hello(new HelloEvent().hostName(HOSTNAME)));

        // users
        final Flux<@NonNull MyServerSentEvent> friends = getFriends(userId)
            .map(userToOpenApiConverter::convert)
            .map(MyServerSentEvent::updateUser);

        // games
        final Flux<@NonNull MyServerSentEvent> games = gameRepository.findGamesByUserId(userId, Integer.MAX_VALUE)
            .map(gameToOpenApiConverter::convert)
            .map(MyServerSentEvent::updateGame);

        // forest
        final Flux<@NonNull MyServerSentEvent> booms = boomRepository.findBoomsByUserId(userId, Integer.MAX_VALUE)
            .map(boomToOpenApiConverter::convert)
            .map(MyServerSentEvent::updateBoom);

        // users
        final Flux<@NonNull MyServerSentEvent> me = Flux.from(userRepository.findById(userId))
            .map(userToOpenApiConverter::convert)
            .map(MyServerSentEvent::updateUser);

        // online list
        final Mono<@NonNull MyServerSentEvent> onlineList = createOnlineListForUser(userId);

        // hello
        final Mono<@NonNull MyServerSentEvent> end = Mono.just(MyServerSentEvent.end());

        return Flux.concat(ping, hello, me, friends, games, booms, onlineList, end);

    }

    @Override
    public Mono<@NonNull MyServerSentEvent> createOnlineListForUser(final String userId) {
        return userRepository.getOnlineFriends(userId).collectList()
            .map(onlineFriends -> MyServerSentEvent.onlineList(new OnlineListEvent().onlineList(onlineFriends)));
    }

    @PostConstruct
    public void postStruct() {
        log.info("Creating heartbeat");
        Flux.interval(Duration.ofSeconds(5))
            .map(aLong -> MyServerSentEvent.ping())
            .subscribe(myServerSentEvent -> {
                mainSink.emitNext(myServerSentEvent, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(5000)));
            });
    }

    @Override
    public void send(final String appIdentifier, final String userId, final MyServerSentEvent myServerSentEvent) {
        //mainSink.emitNext(myServerSentEvent, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(5000)));

        if (appIdentifier != null) {

            final UserChannel userChannel = userChannels.get(appIdentifier);

            if (userChannel != null) {
                userChannel.sink.emitNext(myServerSentEvent, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(3000)));
            }

        } else if (userId != null) {
            userChannels.values()
                .stream()
                .filter(userChannel -> userChannel.userId.equals(userId))
                .forEach(userChannel -> userChannel.sink.emitNext(myServerSentEvent, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(3000))));
        } else {
            log.error("MyServerSentEvent has no appIdentifier or userId, event=" + myServerSentEvent.event());
        }
    }

    @Override
    public Flux<@NonNull ServerSentEvent<@NonNull Object>> subscribe(final String appIdentifier, final String userId, final String remoteAddress, final String userAgent) {

        log.info("{} subscribe() appIdentifier={} userId={}, subscriber={} userChannels={}", remoteAddress, appIdentifier, userId, this.mainSink.currentSubscriberCount(), this.userChannels.size());

        final UserChannel userChannel = userChannels.computeIfAbsent(appIdentifier, id -> new UserChannel(userId));

        final Flux<@NonNull MyServerSentEvent> userFlux = userChannel.sink.asFlux().mergeWith(initCache(appIdentifier, userId));

        final AtomicLong atomicLong = new AtomicLong(1);

        return sseSessionRepository.deleteById(appIdentifier).onErrorResume(DataRetrievalFailureException.class, e -> Mono.empty())
            .then(Mono.just(new SseSession(appIdentifier, remoteAddress, userAgent, HOSTNAME, userId)))
            .flatMap(sseSessionRepository::save)
            .then(sendOnlineListToFriendsOf(userId))
            .thenMany(
                Flux.merge(mainSink.asFlux(), userFlux)
                    .timeout(Duration.ofSeconds(6))
                    .onBackpressureDrop(dropped -> System.out.println("Dropping msg for slow client, event=" + dropped.event()))
                    .doFinally(a -> {
                        log.info("{} doFinally() appIdentifier={} userId={}, subscribers={} userChannels={}", remoteAddress, appIdentifier, userId, this.mainSink.currentSubscriberCount(), this.userChannels.size());
                        userChannels.remove(appIdentifier);
                        sseSessionRepository.deleteById(appIdentifier)
                            .then(sendOnlineListToFriendsOf(userId))
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

//    @Override
//    public void reloadCache(final String appIdentifier, final String userId) {
//        initCache(appIdentifier, userId).subscribe(
//            msg -> mainSink.emitNext(msg, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(1000))),
//            err -> mainSink.emitError(err, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(1000))),
//            () -> { /* Don't close sink if you want to reuse it */ }
//        );
//    }

}
