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
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

import static nl.appsource.cardserver.service.MySseEmitter.createServerSentEvent;

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

    private final Sinks.Many<@NonNull MyServerSentEvent> mainSink = Sinks.many().multicast().onBackpressureBuffer(1024, false);

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

        this.mainSink.emitNext(MySseEmitter.createServerSentEvent("null", "null", "stop"), Sinks.EmitFailureHandler.FAIL_FAST);

        try {
            final Sinks.EmitResult emitResult = this.mainSink.tryEmitComplete();

            if (emitResult.isFailure()) {
                log.error("mainSink.tryEmitComplete() failure");
            }

        } catch (Throwable t) {
            log.error("", t);
        }
    }

    private Flux<@NonNull String> getFriends(final String userId) {
        return userRepository.getFriends(userId);
    }

    private Flux<@NonNull String> getOnlineFriends(final String userId) {
        //return getFriends(userId).filterWhen(this::isUserOnline);
        return userRepository.getOnlineFriends(userId).doOnNext(onlineFriend -> {
                log.info("Found online friend for " + userId + ", fried: " + onlineFriend);
        });
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
////        doSelectedUserIds(userIds, mySseEmitter -> mySseEmitter.message(userMessage));
//    }

    @Override
    public void updateGame(final Game game) {
//        doSelectedUserIds(game.getPlayers(), mySseEmitter -> mySseEmitter.sendUpdateGame(gameToOpenApiConverter.convert(game)));
        final org.openapitools.model.Game convertedGame = gameToOpenApiConverter.convert(game);
        game.getPlayers().forEach(player -> send(createServerSentEvent(null, player, convertedGame)));
    }

    @Override
    public void updateGameForId(final String appIdentifier, final Game game) {
//        doId(appIdentifier, mySseEmitter -> mySseEmitter.sendUpdateGame(requireNonNull(gameToOpenApiConverter.convert(game))));
        send(createServerSentEvent(appIdentifier, null, gameToOpenApiConverter.convert(game)));
    }

    @Override
    public void updateUser(final User user) {

        final org.openapitools.model.User convertedUser = userToOpenApiConverter.convert(user);

        // update self
        send(createServerSentEvent(null, user.getId(), convertedUser));

        // update friends
        getOnlineFriends(user.getId()).subscribe(invite -> send(createServerSentEvent(null, invite, convertedUser)));
//        doSelectedUserIds(user.getInvites(), mySseEmitter -> mySseEmitter.sendUpdateUser(userToOpenApiConverter.convert(user)));
    }

    @Override
    public void updateBoom(final Boom boom) {
//        doSelectedUserIds(boom.getPlayers(), mySseEmitter -> mySseEmitter.sendupdateBoom(boomToOpenApiConverter.convert(boom)));

        final org.openapitools.model.Boom convertedBoom = boomToOpenApiConverter.convert(boom);

        Flux.fromIterable(boom.getPlayers())
            .concatWith(Flux.just(boom.getCreator()))
            .distinct()
            .subscribe(player -> send(createServerSentEvent(null, player, convertedBoom)));

    }

    @Override
    public void newGame(final Game game) {

        final org.openapitools.model.Game convertedGame = gameToOpenApiConverter.convert(game);

        game.getPlayers()
            .stream()
            .filter(player -> !player.equals(game.getCreator()))
            .forEach(player -> send(MySseEmitter.createNewGame(null, player, convertedGame)));

//            .collect(Collectors.toSet()), mySseEmitter -> mySseEmitter.newGame(requireNonNull(gameToOpenApiConverter.convert(game))));
    }

    @Override
    public Mono<Boolean> isUserOnline(final String userId) {
        return sseSessionRepository.existsByCreator(userId);
//            .any(sseSession -> sseSession.getPingReceived() != null
//            && sseSession.getPingReceived().isAfter(Instant.now().minus(Duration.ofSeconds(15)))
//            && sseSession.getPongReceived() != null
//            && sseSession.getPongReceived().isAfter(Instant.now().minus(Duration.ofSeconds(15))));
    }

//    @Override
//    public void newFriend(final String userId, final String friendId) {
////        doUserId(userId, mySseEmitter -> mySseEmitter.newFriend(friendId));
//
//        send(MySseEmitter.newFriend(null, userId, friendId));
//    }

    private Flux<@NonNull MyServerSentEvent> initCache(final String appIdentifier, final String userId) {

        final Flux<@NonNull String> friendIds = userRepository.findById(userId)
            .flatMapMany(user -> Flux.fromIterable(user.getInvites()))
            .mergeWith(userRepository.findIncomingInvites(userId))
            .distinct();

        final Flux<@NonNull MyServerSentEvent> friends = friendIds.collectList()
            .flatMapMany(userRepository::findAllById)
            .map(userToOpenApiConverter::convert)
            .map(user -> createServerSentEvent(appIdentifier, userId, user));

        final Flux<@NonNull MyServerSentEvent> games = gameRepository.findGamesByUserId(userId, Integer.MAX_VALUE)
            .map(gameToOpenApiConverter::convert)
            .map(game -> createServerSentEvent(appIdentifier, userId, game));

        final Flux<@NonNull MyServerSentEvent> booms = boomRepository.findByUserId(userId, Integer.MAX_VALUE)
            .map(boomToOpenApiConverter::convert)
            .map(boom -> createServerSentEvent(appIdentifier, userId, boom));

        final Flux<@NonNull MyServerSentEvent> me = Flux.from(userRepository.findById(userId))
            .map(userToOpenApiConverter::convert)
            .map(user -> createServerSentEvent(appIdentifier, userId, user));

        final Mono<@NonNull MyServerSentEvent> hello = Mono.just(createServerSentEvent(null, null, "hello", null));

        return Flux.concat(me, friends, games, booms, hello);

    }

    @PostConstruct
    public void postStruct() {
        log.info("Creating heartbeat");
        Flux.interval(Duration.ofSeconds(5)).map(aLong -> createServerSentEvent(null, null, "ping")).subscribe(this::send);
    }

    @Override
    public void send(final MyServerSentEvent myServerSentEvent) {
        mainSink.emitNext(myServerSentEvent, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(5000)));
    }

    @Override
    public Flux<@NonNull MyServerSentEvent> subscribe(final String appIdentifier, final String userId, final String remoteAddress, final String userAgent) {

        log.info("{} subscribe() appIdentifier={} userId={}, subscriber={}", remoteAddress, appIdentifier, userId, this.mainSink.currentSubscriberCount());

        return
            sseSessionRepository.deleteById(appIdentifier).onErrorResume(DataRetrievalFailureException.class, e -> Mono.empty())
            .then(Mono.just(new SseSession(appIdentifier, remoteAddress, userAgent, HOSTNAME, userId)))
            .flatMap(sseSessionRepository::save)
            .thenMany(
        mainSink.asFlux()
            .timeout(Duration.ofSeconds(6))
            .filter(myServerSentEvent -> appIdentifier.equals(myServerSentEvent.appIdentifier()) || userId.equals(myServerSentEvent.userId()) || (myServerSentEvent.appIdentifier() == null && myServerSentEvent.userId() == null))
            .mergeWith(Mono.just(createServerSentEvent(appIdentifier, userId, "ping", null)))
            .mergeWith(initCache(appIdentifier, userId))
            .flatMap(myServerSentEvent -> {
                if ("hello".equals(myServerSentEvent.serverSentEvent().event())) {
                    log.info("{} doOnSubscribe() appIdentifier={} userId={}, subscriber={}", remoteAddress, appIdentifier, userId, this.mainSink.currentSubscriberCount());
                    return sendOnlineListTo(userId).then(sendOnlineListToFriendsOf(userId)).then(Mono.just(myServerSentEvent));
                } else {
                    return Mono.just(myServerSentEvent);
                }
            })
            .doFinally(a -> {
                log.info("{} doFinally() appIdentifier={} userId={}, subscriber={}", remoteAddress, appIdentifier, userId, this.mainSink.currentSubscriberCount());
                sseSessionRepository.deleteById(appIdentifier)
                    .then(sendOnlineListToFriendsOf(userId))
                    .subscribe();
            })
            .doOnCancel(() -> {
                log.info("{} doOnCancel() appIdentifier={} userId={}, subscriber={}", remoteAddress, appIdentifier, userId, this.mainSink.currentSubscriberCount());
            })
            .doOnTerminate(() -> {
                log.info("{} doOnTerminate() appIdentifier={} userId={}, subscriber={}", remoteAddress, appIdentifier, userId, this.mainSink.currentSubscriberCount());
            })
            );


    }

    @Override
    public void reloadCache(final String appIdentifier, final String userId) {
        initCache(appIdentifier, userId).subscribe(
            msg -> mainSink.emitNext(msg, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(1000))),
            err -> mainSink.emitError(err, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(1000))),
            () -> { /* Don't close sink if you want to reuse it */ }
        );
    }

}
