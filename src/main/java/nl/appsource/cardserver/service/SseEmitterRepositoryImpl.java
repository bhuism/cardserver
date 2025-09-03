package nl.appsource.cardserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.GameToOpenApiConverter;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.UserRepository;
import org.openapitools.model.SseConnection;
import org.openapitools.model.SseConnectionsEvent;
import org.openapitools.model.UserMessage;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static nl.appsource.cardserver.utils.Utils.isAdmin;

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

    private final ConcurrentHashMap<UUID, MySseEmitter> emitters = new ConcurrentHashMap<>();

    private final GameToOpenApiConverter gameToOpenApiConverter;

    private Predicate<MySseEmitter> forUserId(final String userId) {
        return emitter -> userId.equals(emitter.getUserId());
    }

    private Predicate<MySseEmitter> forUserIds(final Collection<String> userIds) {
        return emitter -> userIds.contains(emitter.getUserId());
    }

    private void doSelectedUserIds(final Collection<String> userIds, final Consumer<MySseEmitter> consumer) {
        emitters.values().stream().filter(forUserIds(userIds)).forEach(consumer);
    }

    private void doUserId(final String userId, final Consumer<MySseEmitter> consumer) {
        emitters.values().stream().filter(forUserId(userId)).forEach(consumer);
    }

    private void doId(final UUID appIdentifier, final Consumer<MySseEmitter> consumer) {
        Optional.ofNullable(emitters.get(appIdentifier)).ifPresentOrElse(consumer, () -> log.error("SseEmitter not found for appIdentifier: {}, got: {} size: {}", appIdentifier, emitters.keys(), emitters.size(), new Throwable()));
    }

    private void doAll(final Consumer<MySseEmitter> consumer) {
        emitters.values().forEach(consumer);
    }

    @Scheduled(fixedDelay = 1000 * 15, initialDelay = 1000 * 30)
    public void pingAll() {
        doAll(MySseEmitter::sendPing);
    }

    private Flux<String> getFriends(final String userId) {
        return userRepository.findById(userId).map(User::getInvites).flatMapMany(list -> userRepository.findIncomingInvites(userId).filter(list::contains));
    }

    @Override
    public void sendOnlineListToFriendsOf(final String userId) {
        getFriends(userId).filter(this::isUserOnline).subscribe(this::sendOnlineListTo);
    }

    @Override
    public void sendOnlineListTo(final String userId) {
        doUserId(userId, mySseEmitter -> mySseEmitter.sendOnlineList(getFriends(userId).filter(this::isUserOnline)));
    }

    @Override
    public void broadCastMessage(final String userId, final String message) {
        userRepository.findById(userId).map(User::getDisplayName).switchIfEmpty(Mono.just(userId)).subscribe(fromString -> doAll(mySseEmitter -> mySseEmitter.message(new UserMessage().userId(userId).message(fromString + ": " + message))));
    }

    @Override
    public void ping(final UUID appIdentifier) {
        if (this.emitters.containsKey(appIdentifier)) {
            doId(appIdentifier, MySseEmitter::receivePing);
        } else {
            log.warn("ping() AppIdentifier {} not found", appIdentifier);
        }
    }

    @Override
    public void pong(final UUID appIdentifier) {
        doId(appIdentifier, MySseEmitter::receivePong);
    }

    @Override
    public void friendsChanged(final Collection<String> userIds) {
        doSelectedUserIds(userIds, MySseEmitter::sendUpdateFriends);
    }

    @Override
    public void gamesChanged(final Collection<String> userIds) {
        doSelectedUserIds(userIds, MySseEmitter::sendUpdateGames);
    }

    @Override
    public void updateGameState(final Game game) {
        final org.openapitools.model.Game convertedGame = gameToOpenApiConverter.convert(game);
        doSelectedUserIds(game.getPlayers(), mySseEmitter -> mySseEmitter.sendUpdateGameState(requireNonNull(convertedGame)));
    }

    @Override
    public void newGame(final Game game) {
        doSelectedUserIds(game.getPlayers().stream().filter(player -> !player.equals(game.getCreator())).collect(Collectors.toSet()), mySseEmitter -> mySseEmitter.newGame(requireNonNull(gameToOpenApiConverter.convert(game))));
    }

    @Override
    public boolean isUserOnline(final String userId) {
        return emitters.values().stream().anyMatch(forUserId(userId));
    }

    @Override
    public void newFriend(final String userId, final String friendId) {
        doUserId(userId, mySseEmitter -> mySseEmitter.newFriend(friendId));
    }


    @Override
    public void sendAppIdentifierMessage(final UUID appIdentifier, final UserMessage userMessage) {
        doId(appIdentifier, mySseEmitter -> mySseEmitter.message(userMessage));
    }

    private Flux<ServerSentEvent<?>> createSseConnectionsEventFlux() {
        return Flux.interval(Duration.ofSeconds(1))
            .map((_counter) -> {
                final SseConnectionsEvent sseConnectionsEvent = new SseConnectionsEvent();

                final List<SseConnection> events = emitters.entrySet().stream().map(mySseEmitterEntry -> {

                    final SseConnection sseConnection = new SseConnection();

                    sseConnection.id(mySseEmitterEntry.getKey().toString());
                    sseConnection.userId(mySseEmitterEntry.getValue().getUserId());
                    sseConnection.pingReceived(mySseEmitterEntry.getValue().getPingReceived());
                    sseConnection.pingSent(mySseEmitterEntry.getValue().getPingSent());
                    sseConnection.pongReceived(mySseEmitterEntry.getValue().getPongReceived());
                    sseConnection.pongSent(mySseEmitterEntry.getValue().getPongSent());

                    return sseConnection;

                }).toList();

                sseConnectionsEvent.events(events);

                return sseConnectionsEvent;
            })
            .map(data -> MySseEmitter.createServerSentEvent("sseConnectionsEvent", data));
    }

    @Override
    public Flux<ServerSentEvent<?>> subscribe(final UUID appIdentifier, final String userId, final String remoteAddress) {

        final MySseEmitter mySseEmitter = new MySseEmitter(userId);

        emitters.put(appIdentifier, mySseEmitter);

        return Flux.just(
                mySseEmitter.createPingEvent(), mySseEmitter.createPingEvent(), mySseEmitter.createPingEvent())
            .concatWith(mySseEmitter.subscribe())
            .mergeWith(isAdmin(userId) ? createSseConnectionsEventFlux() : Flux.empty())
            .doOnSubscribe((s) -> {
                log.info("{} subscribe() appIdentifier={} userId={} count={}", remoteAddress, appIdentifier, userId, emitters.size());
                sendOnlineListTo(userId);
                sendOnlineListToFriendsOf(userId);
            }).doFinally((s) -> {
                log.info("{} unSubscribe() appIdentifier={}  userId={}, count={} signal={}", remoteAddress, appIdentifier, userId, emitters.size(), s.toString());
                emitters.remove(appIdentifier);
                mySseEmitter.close();
                sendOnlineListToFriendsOf(userId);
            });
    }

}
