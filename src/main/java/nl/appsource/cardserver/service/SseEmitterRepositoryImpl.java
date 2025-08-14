package nl.appsource.cardserver.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.GameToOpenApiConverter;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.UserRepository;
import org.openapitools.model.UserMessage;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Service
@Slf4j
@RequiredArgsConstructor
public class SseEmitterRepositoryImpl implements SseEmitterRepository {

    private final UserRepository userRepository;

    private final ConcurrentHashMap<UUID, MySseEmitter> emitters = new ConcurrentHashMap<>();

    private final GameToOpenApiConverter gameToOpenApiConverter;

    private void doSelectedUserIds(final Flux<String> userIds, final Consumer<MySseEmitter> consumer) {
        userIds.flatMap(userId -> Flux.fromIterable(emitters.values()).filter(emitter -> emitter.getUserId().equals(userId))).subscribe(consumer);
    }

    private void doUserId(final String userId, final Consumer<MySseEmitter> consumer) {
        Flux.fromIterable(emitters.values()).filter(emitter -> emitter.getUserId().equals(userId)).subscribe(consumer);
    }

    private void doId(final UUID uuid, final Consumer<MySseEmitter> consumer) {
        Optional.ofNullable(emitters.get(uuid))
            .ifPresentOrElse(consumer, () -> log.error("SseEmitter not found for uuid {}", uuid));
    }


    private void doAll(final Consumer<MySseEmitter> consumer) {
        Flux.fromIterable(emitters.values()).subscribe(consumer);
    }

    @Scheduled(fixedDelay = 1000 * 15, initialDelay = 1000 * 30)
    public void pingAll() {
        doAll(MySseEmitter::sendPing);
    }

//    @Scheduled(fixedDelay = 1000 * 15, initialDelay = 1000 * 60)
//    public void pingUpdateStatusAll() {
//        doAll(this::pingUpdateStatus);
//    }

    @Scheduled(fixedDelay = 1000 * 15, initialDelay = 1000 * 5)
    public void janitor() {
        final Set<UUID> removers = new HashSet<>();
        emitters.forEach((uuid, mySseEmitter) -> {
            if (mySseEmitter.getCancelled() != null) {
                try {
                    mySseEmitter.tryEmitComplete();
                } catch (final RuntimeException e) {
                    log.error("", e);
                } finally {
                    removers.add(uuid);
                }
            }
        });

        removers.forEach(emitters::remove);

    }

    private Flux<String> getFriends(final String userId) {
        return userRepository.findById(userId)
            .map(User::getInvites)
            .flatMapMany(list -> userRepository.findIncomingInvites(userId).filter(list::contains));
    }

    @Override
    public void sendOnlineListToFriendsOf(final String userId) {
        getFriends(userId).filter(this::isUserOnline).subscribe(this::sendOnlineListTo);
    }

    @Override
    public void sendOnlineListTo(final String userId) {
        doUserId(userId, this::_sendOnlineList);
    }

    private void _sendOnlineList(final MySseEmitter mySseEmitter) {
        mySseEmitter.sendOnlineList(getFriends(mySseEmitter.getUserId()).filter(this::isUserOnline));
    }

    @Override
    public void broadCastMessage(final String userId, final String message) {
        userRepository.findById(userId)
            .map(User::getDisplayName)
            .switchIfEmpty(Mono.just(userId))
            .subscribe(fromString -> doAll(mySseEmitter -> mySseEmitter.message(new UserMessage().userId(userId).message(fromString + ": " + message))));
    }

    @PreDestroy
    public void destroy() {
        janitor();
    }

    @Override
    public void ping(final UUID uuid) {
        doId(uuid, MySseEmitter::receivePing);
    }

    @Override
    public void pong(final UUID uuid) {
        doId(uuid, MySseEmitter::receivePong);
    }

    @Override
    public void friendsChanged(final Collection<String> userIds) {
        doSelectedUserIds(Flux.fromIterable(userIds), MySseEmitter::sendUpdateFriends);
    }

    @Override
    public void gamesChanged(final Collection<String> userIds) {
        doSelectedUserIds(Flux.fromIterable(userIds), MySseEmitter::sendUpdateGames);
    }

    @Override
    public void newGame(final Game game) {
        doSelectedUserIds(Flux.fromIterable(game.getPlayers()).filter(player -> !player.equals(game.getCreator())), mySseEmitter -> mySseEmitter.newGame(Objects.requireNonNull(gameToOpenApiConverter.convert(game))));
    }

    @Override
    public boolean isUserOnline(final String userId) {
        return emitters.values().stream().anyMatch(emitter -> emitter.getUserId().equals(userId));
    }

    @Override
    public void newFriend(final String userId, final String friendId) {
        doUserId(userId, mySseEmitter -> mySseEmitter.newFriend(friendId));
    }

    @Override
    public Flux<ServerSentEvent<Object>> subscribe(final String userId) {

        final MySseEmitter mySseEmitter = new MySseEmitter(userId);

        emitters.put(mySseEmitter.getUuid(), mySseEmitter);

        return Flux.just(mySseEmitter.createPingEvent().serverSentEvent(), mySseEmitter.createPingEvent().serverSentEvent(), mySseEmitter.createPingEvent().serverSentEvent())
            .concatWith(mySseEmitter.subscribe())
            .doOnSubscribe((s) -> {
                log.info("subscribe() userId={}, sseEmitter={} ", userId, mySseEmitter.getUuid());
                sendOnlineListTo(userId);
                sendOnlineListToFriendsOf(userId);
            })
            .doOnCancel(() -> {
                log.info("unSubscribe() userId={}, sseEmitter={} ", userId, mySseEmitter.getUuid());
                mySseEmitter.cancel();
                janitor();

                try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
                    executor.submit(() -> {
                        try {
                            Thread.sleep(1000);
                            sendOnlineListToFriendsOf(userId);
                        } catch (final InterruptedException e) {
                            log.error("", e);
                        }
                    });
                    executor.shutdown();
                }
            });

    }

}
