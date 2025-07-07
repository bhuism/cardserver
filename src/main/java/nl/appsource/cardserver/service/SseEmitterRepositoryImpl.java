package nl.appsource.cardserver.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.UserRepository;
import org.openapitools.model.Game;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
@Slf4j
@RequiredArgsConstructor
public class SseEmitterRepositoryImpl implements SseEmitterRepository {

    private final UserRepository userRepository;

    private final ConcurrentHashMap<UUID, MySseEmitter> emitters = new ConcurrentHashMap<>();

    private void doSelectedUserIds(final Flux<String> userIds, final Consumer<MySseEmitter> consumer) {
        userIds.flatMap(userId -> Flux.fromIterable(emitters.values()).filter(emitter -> emitter.getUserId().equals(userId))).subscribe(consumer);
    }

    private void doId(final UUID uuid, final Consumer<MySseEmitter> consumer) {
        Optional.ofNullable(emitters.get(uuid))
            .ifPresentOrElse(consumer, () -> log.error("SseEmitter not found for uuid {}", uuid));
    }


    private void doAll(final Consumer<MySseEmitter> consumer) {
        Flux.fromIterable(emitters.values()).subscribe(consumer);
    }

    @Scheduled(fixedDelay = 1000 * 60, initialDelay = 1000 * 55)
    public void pingAll() {
//        log.info("Current subscriber count: {}", .currentSubscriberCount());
        doAll(MySseEmitter::sendPing);
    }


    @Scheduled(fixedDelay = 1000 * 15, initialDelay = 1000 * 60)
    public void pingUpdateStatusAll() {
        doAll(this::pingUpdateStatus);
    }

    @Scheduled(fixedDelay = 1000 * 60, initialDelay = 1000 * 5)
    public void janitor() {
        final Set<UUID> removers = new HashSet<>();
        emitters.forEach((uuid, mySseEmitter) -> {
            if (mySseEmitter.getCancelled() != null) {
                try {
                    log.info("Cleaning emitter: {}", mySseEmitter.getUuid());
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

    private void pingUpdateStatus(final MySseEmitter mySseEmitter) {
        userRepository.findById(mySseEmitter.getUserId())
            .map(User::getInvites)
            .map(list -> {
                userRepository.findIncomingInvites(mySseEmitter.getUserId()).map(User::getId).collectList().subscribe(list::retainAll);
                return list;
            })
            .map(list -> {
                list.retainAll(emitters.values().stream().map(MySseEmitter::getUserId).toList());
                return list;
            })
            .subscribe(mySseEmitter::sendOneList);
    }

    @Override
    public void broadCastMessage(final String userId, final String message) {
        userRepository.findById(userId)
            .map(User::getDisplayName)
            .switchIfEmpty(Mono.just(userId))
            .subscribe(fromString -> {
                doAll(mySseEmitter -> mySseEmitter.sendCardServerMessage(fromString, message));
            });
    }

    @PreDestroy
    public void destroy() {
        janitor();
    }

    @Override
    public Flux<ServerSentEvent<Object>> subscribe(final String userId) {

        final MySseEmitter mySseEmitter = new MySseEmitter(userId);

        log.info("subscribe() userId={}, sseEmitter={} count={}", userId, mySseEmitter.getUuid(), emitters.mappingCount());

        emitters.put(mySseEmitter.getUuid(), mySseEmitter);

        mySseEmitter.sendPing();

        Flux.range(1, 5)
            .delayElements(Duration.ofMillis(500), Schedulers.single()).subscribe(integer -> mySseEmitter.sendPing());

        pingUpdateStatus(mySseEmitter);

        return mySseEmitter.subscribe().doOnCancel(() -> {
            mySseEmitter.cancel();
            janitor();
            pingUpdateStatusAll();
        });

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
    public Game gameChanged(final Game gameState) {
        doSelectedUserIds(Flux.fromIterable(gameState.getPlayers()), mySseEmitter -> mySseEmitter.sendGameChanged(gameState));
        return gameState;
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
    public boolean isUserOnline(final String userId) {
        return emitters.values().stream().anyMatch(emitter -> emitter.getUserId().equals(userId));
    }


}
