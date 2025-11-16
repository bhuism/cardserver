package nl.appsource.cardserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.GameToOpenApiConverter;
import nl.appsource.cardserver.converter.UserToOpenApiConverter;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.UserRepository;
import org.openapitools.model.SseConnection;
import org.openapitools.model.SseConnections;
import org.openapitools.model.UserMessage;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

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
    private final UserToOpenApiConverter userToOpenApiConverter;

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
            .ifPresentOrElse(consumer, () -> emitters.remove(appIdentifier));
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
    public void updateGame(final Game game) {
        final String topic = "game" + game.getId();
        Optional.ofNullable(this.topics.get(topic))
            .ifPresent(uuids -> {
                uuids.forEach(appIdentifier -> {
                    updateGameForId(appIdentifier, game);
                });
            });
    }

    @Override
    public void updateGameForId(final UUID appIdentifier, final Game game) {
        doId(appIdentifier, mySseEmitter -> mySseEmitter.sendUpdateGame(requireNonNull(gameToOpenApiConverter.convert(game))));
    }

    @Override
    public void updateUser(final User user) {
        final String topic = "user" + user.getId();
        Optional.ofNullable(this.topics.get(topic))
            .ifPresent(uuids -> {
                uuids.forEach(appIdentifier -> {
                    doId(appIdentifier, mySseEmitter -> mySseEmitter.sendUpdateUser(requireNonNull(userToOpenApiConverter.convert(user))));
                });
            });
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

    @Override
    public SseConnections getDebugSseConnections() {

        final SseConnections getDebugSseConnections200Response = new SseConnections();

        final List<SseConnection> events = emitters.entrySet()
            .stream()
            .map(mySseEmitterEntry -> {

                final SseConnection sseConnection = new SseConnection();

                sseConnection.id(mySseEmitterEntry.getKey().toString());
                sseConnection.userId(mySseEmitterEntry.getValue().getUserId());
                sseConnection.appId(mySseEmitterEntry.getKey().toString());
                sseConnection.pingReceived(mySseEmitterEntry.getValue().getPingReceived());
                sseConnection.pingReceivedCount(mySseEmitterEntry.getValue().getPingReceivedCount());
                sseConnection.pingSent(mySseEmitterEntry.getValue().getPingSent());
                sseConnection.pingSentCount(mySseEmitterEntry.getValue().getPingSentCount());
                sseConnection.pongReceived(mySseEmitterEntry.getValue().getPongReceived());
                sseConnection.pongReceivedCount(mySseEmitterEntry.getValue().getPongReceivedCount());
                sseConnection.pongSent(mySseEmitterEntry.getValue().getPongSent());
                sseConnection.pongSentCount(mySseEmitterEntry.getValue().getPongSentCount());

                final List<String> subscriptions = new ArrayList<>();

                this.topics.forEach((topic, uuids) -> {
                    if (uuids.contains(mySseEmitterEntry.getKey())) {
                        subscriptions.add(topic);
                    }
                });

                sseConnection.subscriptions(subscriptions);

                return sseConnection;

            })
            .toList();

        getDebugSseConnections200Response.timeStamp(Instant.now());
        getDebugSseConnections200Response.events(events);

        return getDebugSseConnections200Response;

    }

    @Override
    public Flux<ServerSentEvent<?>> subscribe(final UUID appIdentifier, final String userId, final String remoteAddress) {

        final MySseEmitter mySseEmitter = new MySseEmitter(userId);

        emitters.put(appIdentifier, mySseEmitter);

        return mySseEmitter.subscribe()
            .doFinally((s) -> {
                log.info("{} unSubscribe() appIdentifier={} userId={}, count={}", remoteAddress, appIdentifier, userId, emitters.size());
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
            log.error("Emitter not found for " + appIdentifier + ", got: " + joiner);
            return false;
        }

        if (!mySseEmitter.getUserId()
            .equals(userId)) {
            log.error("Emitter has wrong userId");
            return false;
        }

        return true;
    }

    private final Map<String, List<UUID>> topics = new ConcurrentHashMap<>();

    @Override
    public void eventSubscribe(final UUID appIdentifier, final String topic) {
        topics.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(appIdentifier);
    }

    @Override
    public void eventUnSubscribe(final UUID appIdentifier, final String topic) {
        Optional.ofNullable(topics.get(topic)).ifPresent(subscribers -> subscribers.remove(appIdentifier));
    }

    @Override
    public int getSubscribtionCount(final String topic) {
        return Optional.ofNullable(topics.get(topic)).map(List::size).orElse(0);
    }

}
