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
import org.openapitools.model.SseConnection;
import org.openapitools.model.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import static nl.appsource.cardserver.utils.IDTYPE.SESS;

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

    private Flux<@NonNull String> getFriends(final String userId) {
        return userRepository.findById(userId)
            .map(User::getInvites)
            .flatMapMany(list -> userRepository.findIncomingInvites(userId)
                .filter(list::contains));
    }

    @Override
    public void sendOnlineListToFriendsOf(final String userId) {
        createSendOnlineListToFriendsOf(userId)
            .subscribe(this::sendOnlineListTo);
    }

    private Flux<@NonNull String> createSendOnlineListToFriendsOf(final String userId) {
        return getFriends(userId)
            .filterWhen(this::isUserOnline);
    }

    @Override
    public void sendOnlineListTo(final String userId) {
        createOnlineListTo(userId).subscribe(this::send);
//        send(createOnlineListTo(userId).toStream().toList());
//        doUserId(userId, mySseEmitter -> mySseEmitter.sendOnlineList(getFriends(userId).filter(this::isUserOnline)));
    }

    private Mono<@NonNull MyServerSentEvent> createOnlineListTo(final String userId) {

        return getFriends(userId).filterWhen(this::isUserOnline).collectList().map(list -> {
            return MySseEmitter.createOnlineList(null, userId, list);
        });
//        return MySseEmitter.createOnlineList(null, userId, getFriends(userId).filter(this::isUserOnline);
//        doUserId(userId, mySseEmitter -> mySseEmitter.sendOnlineList(getFriends(userId).filter(this::isUserOnline)));
    }


    @Override
    public void sendMessage(final Collection<String> userIds, final UserMessage userMessage) {
        userIds.forEach(userId -> {
            send(MySseEmitter.createMessageEvent(null, userId, userMessage));
        });
//        doSelectedUserIds(userIds, mySseEmitter -> mySseEmitter.message(userMessage));
    }

    @Override
    public Mono<Void> ping(final UUID appIdentifier) {
        return sseSessionRepository.findById(SESS.getIdentifier() + appIdentifier.toString())
            .map(sseSession -> {
                sseSession.ping();
                send(MySseEmitter.createPongEvent(appIdentifier, null));
                return sseSession;
            })
            .flatMap(sseSessionRepository::save)
            .then();
    }

    @Override
    public Mono<Void> pong(final UUID appIdentifier) {
        return sseSessionRepository.findById(SESS.getIdentifier() + appIdentifier.toString())
            .doOnNext(SseSession::pong)
            .flatMap(sseSessionRepository::save)
            .then();
//        doId(appIdentifier, MySseEmitter::receivePong);
    }

    @Override
    public void friendsChanged(final Collection<String> userIds) {
//        doSelectedUserIds(userIds, MySseEmitter::sendUpdateFriends);
        userIds.forEach(userId -> {
            send(MySseEmitter.createUpdateFriends(null, userId));
        });
    }

    @Override
    public void gamesChanged(final Collection<String> userIds) {
        userIds.forEach(userId -> {
            send(MySseEmitter.createUpdateGames(null, userId));
        });
//        doSelectedUserIds(userIds, MySseEmitter::sendUpdateGames);
    }

    @Override
    public void boomsChanged(final Collection<String> userIds) {
        userIds.forEach(userId -> {
            send(MySseEmitter.createUpdateBooms(null, userId));
        });
//        doSelectedUserIds(userIds, MySseEmitter::sendUpdateBooms);
    }

    @Override
    public void updateGame(final Game game) {
//        doSelectedUserIds(game.getPlayers(), mySseEmitter -> mySseEmitter.sendUpdateGame(gameToOpenApiConverter.convert(game)));
        final org.openapitools.model.Game convertedGame = gameToOpenApiConverter.convert(game);
        game.getPlayers().forEach(player -> send(MySseEmitter.createServerSentEvent(null, player, convertedGame)));
    }

    @Override
    public void updateGameForId(final UUID appIdentifier, final Game game) {
//        doId(appIdentifier, mySseEmitter -> mySseEmitter.sendUpdateGame(requireNonNull(gameToOpenApiConverter.convert(game))));
        send(MySseEmitter.createServerSentEvent(appIdentifier, null, gameToOpenApiConverter.convert(game)));
    }

    @Override
    public void updateUserForId(final UUID appIdentifier, final User user) {
//        doId(appIdentifier, mySseEmitter -> mySseEmitter.sendUpdateUser(requireNonNull(userToOpenApiConverter.convert(user))));
        send(MySseEmitter.createServerSentEvent(appIdentifier, null, userToOpenApiConverter.convert(user)));
    }

    @Override
    public void updateUser(final User user) {

        final org.openapitools.model.User convertedUser = userToOpenApiConverter.convert(user);

        // update self
        send(MySseEmitter.createServerSentEvent(null, user.getId(), convertedUser));

        // update friends
        getFriends(user.getId()).subscribe(invite -> {
            send(MySseEmitter.createServerSentEvent(null, invite, convertedUser));
        });
//        doSelectedUserIds(user.getInvites(), mySseEmitter -> mySseEmitter.sendUpdateUser(userToOpenApiConverter.convert(user)));
    }

    @Override
    public void updateBoom(final Boom boom) {
//        doSelectedUserIds(boom.getPlayers(), mySseEmitter -> mySseEmitter.sendupdateBoom(boomToOpenApiConverter.convert(boom)));

        final org.openapitools.model.Boom convertedBoom = boomToOpenApiConverter.convert(boom);

        Flux.fromIterable(boom.getPlayers())
            .concatWith(Flux.just(boom.getCreator()))
            .distinct()
            .subscribe(player -> {
                send(MySseEmitter.createServerSentEvent(null, player, convertedBoom));
            });

    }

    @Override
    public void newGame(final Game game) {

        final org.openapitools.model.Game convertedGame = gameToOpenApiConverter.convert(game);

        game.getPlayers()
            .stream()
            .filter(player -> !player.equals(game.getCreator()))
            .forEach(player -> {
                send(MySseEmitter.createNewGame(null, player, convertedGame));
            });

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

    @Override
    public void newFriend(final String userId, final String friendId) {
//        doUserId(userId, mySseEmitter -> mySseEmitter.newFriend(friendId));

        send(MySseEmitter.newFriend(null, userId, friendId));
    }


    @Override
    public void sendAppIdentifierMessage(final UUID appIdentifier, final UserMessage userMessage) {
//        doId(appIdentifier, mySseEmitter -> mySseEmitter.message(userMessage));
        send(MySseEmitter.createMessageEvent(appIdentifier, null, userMessage));
    }

    public record DebugSseConnections(Flux<SseConnection> connections, Instant timeStamp, Integer subscriberCount) {
    }

    @Override
    public DebugSseConnections getDebugSseConnections() {
        return new DebugSseConnections(
            sseSessionRepository.findAll()
                .map(mySseEmitterEntry -> {
                    final SseConnection sseConnection = new SseConnection();
                    sseConnection.setId(mySseEmitterEntry.getId());
                    sseConnection.setCreated(mySseEmitterEntry.getCreated());
                    sseConnection.setUpdated(Optional.ofNullable(mySseEmitterEntry.getUpdated()));
                    sseConnection.setCreator(mySseEmitterEntry.getCreator());
                    sseConnection.setPingReceived(Optional.ofNullable(mySseEmitterEntry.getPingReceived()));
                    sseConnection.setPingReceivedCount(mySseEmitterEntry.getPingReceivedCount());
                    sseConnection.setPongReceived(Optional.ofNullable(mySseEmitterEntry.getPongReceived()));
                    sseConnection.setPongReceivedCount(mySseEmitterEntry.getPongReceivedCount());
                    sseConnection.setRemoteAddress(mySseEmitterEntry.getRemoteAddress());
                    sseConnection.setUserAgent(mySseEmitterEntry.getUserAgent());
                    return sseConnection;
                }),
                Instant.now(),
                this.mainSink.currentSubscriberCount()
                );
    }

    private Flux<@NonNull MyServerSentEvent> initCache(final UUID appIdentifier, final String userId) {

        final Flux<@NonNull String> friendIds = userRepository.findById(userId)
            .flatMapMany(user -> Flux.fromIterable(user.getInvites()))
            .mergeWith(userRepository.findIncomingInvites(userId))
            .distinct();

        final Flux<@NonNull MyServerSentEvent> friends = friendIds.collectList()
            .flatMapMany(userRepository::findAllById)
            .map(userToOpenApiConverter::convert)
            .map(user -> MySseEmitter.createServerSentEvent(appIdentifier, userId, user));

        final Flux<@NonNull MyServerSentEvent> games = gameRepository.findGamesByUserId(userId, Integer.MAX_VALUE)
            .map(gameToOpenApiConverter::convert)
            .map(game -> MySseEmitter.createServerSentEvent(appIdentifier, userId, game));

        final Flux<@NonNull MyServerSentEvent> booms = boomRepository.findByUserId(userId, Integer.MAX_VALUE)
            .map(boomToOpenApiConverter::convert)
            .map(boom -> MySseEmitter.createServerSentEvent(appIdentifier, userId, boom));

        final Flux<@NonNull MyServerSentEvent> me = Flux.from(userRepository.findById(userId))
            .map(userToOpenApiConverter::convert)
            .map(user -> MySseEmitter.createServerSentEvent(appIdentifier, userId, user));

        final Mono<@NonNull MyServerSentEvent> hello = Mono.just(MySseEmitter.createServerSentEvent(null, null, "hello", null));

        return Flux.concat(me, friends, games, booms, hello);

    }

    @PostConstruct
    public void postStruct() {
        log.info("Creating heartbeat");
        Flux.interval(Duration.ofSeconds(15)).map(MySseEmitter::createPingEvent).subscribe(this::send);
    }

    private void send(final MyServerSentEvent myServerSentEvent) {
        mainSink.emitNext(myServerSentEvent, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(5000)));
    }

    @Override
    public Flux<@NonNull MyServerSentEvent> subscribe(final UUID appIdentifier, final String userId, final String remoteAddress, final String userAgent) {

        log.info("{} subscribe() appIdentifier={} userId={}, subscriber={}", remoteAddress, appIdentifier, userId, this.mainSink.currentSubscriberCount());

        final String id = SESS.getIdentifier() + appIdentifier.toString();

        return Mono.just(new SseSession(id, remoteAddress, userAgent, userId))
            .flatMap(sseSessionRepository::save)
            .thenMany(
        mainSink.asFlux()
            .filter(myServerSentEvent -> appIdentifier.equals(myServerSentEvent.getAppIdentifier()) || userId.equals(myServerSentEvent.getUserId()) || (myServerSentEvent.getAppIdentifier() == null && myServerSentEvent.getUserId() == null))
            .mergeWith(Mono.just(MySseEmitter.createServerSentEvent(appIdentifier, userId, "ping", null)))
            .mergeWith(initCache(appIdentifier, userId))
            .doOnSubscribe(signalType -> {
                sendOnlineListTo(userId);
                sendOnlineListToFriendsOf(userId);
            })
            .doFinally((a) -> {
                sseSessionRepository.deleteById(appIdentifier.toString())
                    .doOnSuccess(_ -> {
                        sendOnlineListToFriendsOf(userId);
                    })
                    .subscribe();
                log.info("{} cancel() appIdentifier={} userId={}, subscriber={}", remoteAddress, appIdentifier, userId, this.mainSink.currentSubscriberCount());
            })
            );


    }

    @Override
    public void reloadCache(final UUID appIdentifier, final String userId) {
        initCache(appIdentifier, userId).subscribe(
            msg -> mainSink.emitNext(msg, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(1000))),
            err -> mainSink.emitError(err, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(1000))),
            () -> { /* Don't close sink if you want to reuse it */ }
        );
//        doId(appIdentifier, mySseEmitter -> mySseEmitter.sendFlux(initCache(appIdentifier, userId)));
    }

//    @Override
//    public Mono<User> validate(final UUID appIdentifier, final User user) {
//        return sseSessionRepository.findByIdAndCreator(SESS.getIdentifier() + appIdentifier.toString(), user.getId())
////            .flatMap(sseSessionRepository::save)
//            .then(Mono.just(user));
//    }

}
