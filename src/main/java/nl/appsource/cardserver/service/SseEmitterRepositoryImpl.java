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
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
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
        emitters.values()
            .stream()
            .filter(forUserIds(userIds))
            .forEach(consumer);
    }

    private void doUserId(final String userId, final Consumer<MySseEmitter> consumer) {
        emitters.values()
            .stream()
            .filter(forUserId(userId))
            .forEach(consumer);
    }

    private void doUserIds(final Collection<String> userIds, final Consumer<MySseEmitter> consumer) {
        emitters.values()
            .stream()
            .filter(forUserIds(userIds))
            .forEach(consumer);
    }

    private void doId(final UUID appIdentifier, final Consumer<MySseEmitter> consumer) {
        Optional.ofNullable(emitters.get(appIdentifier))
            .ifPresentOrElse(consumer, () -> {

                final StringJoiner joiner = new StringJoiner(",");
                this.emitters.keys()
                    .asIterator()
                    .forEachRemaining(uuid -> {
                        joiner.add(uuid.toString());
                    });

                log.error("SseEmitter not found for appIdentifier: {}, got: {} size: {}", appIdentifier, joiner.toString(), emitters.size(), new Throwable());
            });
    }

    private Flux<String> getFriends(final String userId) {
        return userRepository.findById(userId)
            .map(User::getInvites)
            .flatMapMany(list -> userRepository.findIncomingInvites(userId)
                .filter(list::contains));
    }

    @Override
    public void sendOnlineListToFriendsOf(final String userId) {
        getFriends(userId)
            .filter(this::isUserOnline)
            .subscribe(this::sendOnlineListTo);
    }

    @Override
    public void sendOnlineListTo(final String userId) {
        doUserId(userId, mySseEmitter -> mySseEmitter.sendOnlineList(getFriends(userId).filter(this::isUserOnline)));
    }


    @Override
    public void sendMessage(final Collection<String> userIds, final UserMessage userMessage) {
        doUserIds(userIds, mySseEmitter -> mySseEmitter.message(userMessage));
    }

    @Override
    public void ping(final UUID appIdentifier) {
        doId(appIdentifier, MySseEmitter::receivePing);
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
    public void updateGameStateAllPlayers(final Game game) {
        final org.openapitools.model.Game convertedGame = gameToOpenApiConverter.convert(game);
        doSelectedUserIds(game.getPlayers(), mySseEmitter -> mySseEmitter.sendUpdateGameState(requireNonNull(convertedGame)));
    }

    public void updateGameStateForId(final UUID appIdentifier, final Game game) {
        final org.openapitools.model.Game convertedGame = gameToOpenApiConverter.convert(game);
        doId(appIdentifier, mySseEmitter -> mySseEmitter.sendUpdateGameState(requireNonNull(convertedGame)));
    }

    @Override
    public void newGame(final Game game) {
        doSelectedUserIds(game.getPlayers()
            .stream()
            .filter(player -> !player.equals(game.getCreator()))
            .collect(Collectors.toSet()), mySseEmitter -> mySseEmitter.newGame(requireNonNull(gameToOpenApiConverter.convert(game))));
    }

    @Override
    public boolean isUserOnline(final String userId) {
        return emitters.values()
            .stream()
            .anyMatch(forUserId(userId));
    }

    @Override
    public void newFriend(final String userId, final String friendId) {
        doUserId(userId, mySseEmitter -> mySseEmitter.newFriend(friendId));
    }


    @Override
    public void sendAppIdentifierMessage(final UUID appIdentifier, final UserMessage userMessage) {
        doId(appIdentifier, mySseEmitter -> mySseEmitter.message(userMessage));
    }

    private Flux<SseConnectionsEvent> createSseConnectionsEventFlux() {
        return Flux.interval(Duration.ofSeconds(1))
            .map((_counter) -> {
                final SseConnectionsEvent sseConnectionsEvent = new SseConnectionsEvent();

                final List<SseConnection> events = emitters.entrySet()
                    .stream()
                    .map(mySseEmitterEntry -> {

                        final SseConnection sseConnection = new SseConnection();

                        sseConnection.id(mySseEmitterEntry.getKey()
                            .toString());
                        sseConnection.userId(mySseEmitterEntry.getValue()
                            .getUserId());
                        sseConnection.pingReceived(mySseEmitterEntry.getValue()
                            .getPingReceived());
                        sseConnection.pingSent(mySseEmitterEntry.getValue()
                            .getPingSent());
                        sseConnection.pongReceived(mySseEmitterEntry.getValue()
                            .getPongReceived());
                        sseConnection.pongSent(mySseEmitterEntry.getValue()
                            .getPongSent());

                        return sseConnection;

                    })
                    .toList();

                sseConnectionsEvent.timeStamp(Instant.now());
                sseConnectionsEvent.events(events);

                return sseConnectionsEvent;
            });
    }

    @Override
    public Flux<ServerSentEvent<?>> subscribe(final UUID appIdentifier, final String userId, final String remoteAddress) {

        final MySseEmitter mySseEmitter = new MySseEmitter(userId);

        emitters.put(appIdentifier, mySseEmitter);

        return mySseEmitter.subscribe()
            .mergeWith(isAdmin(userId) ? createSseConnectionsEventFlux().map(mySseEmitter::createSseConnectionsEvent) : Flux.empty())
            .doFinally((s) -> {
                log.info("{} unSubscribe() appIdentifier={}  userId={}, count={}", remoteAddress, appIdentifier, userId, emitters.size());
                emitters.remove(appIdentifier);
                mySseEmitter.close();
                sendOnlineListToFriendsOf(userId);
            })
            .doOnSubscribe((s) -> {
                log.info("{} subscribe() appIdentifier={} userId={} count={}", remoteAddress, appIdentifier, userId, emitters.size());
                sendOnlineListTo(userId);
                sendOnlineListToFriendsOf(userId);
            });
    }

    @Override
    public Boolean validate(final UUID appIdentifier, final String userId) {

        final MySseEmitter mySseEmitter = emitters.get(appIdentifier);

        if (mySseEmitter == null) {
            final StringJoiner joiner = new StringJoiner(",");
            this.emitters.keys()
                .asIterator()
                .forEachRemaining(uuid -> {
                    joiner.add(uuid.toString());
                });
            log.error("Emitter not found for " + appIdentifier + ", got: " + joiner.toString());
            return false;
        }

        if (!mySseEmitter.getUserId()
            .equals(userId)) {
            log.error("Emitter has wrong userId");
            return false;
        }

        return true;
    }

}
