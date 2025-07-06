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
import reactor.core.publisher.Sinks;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

@Service
@Slf4j
@RequiredArgsConstructor
public class SseEmitterRepositoryImpl implements SseEmitterRepository {

    private final UserRepository userRepository;

    private final Sinks.Many<UserServerSentEvent> manySinks = Sinks.many().multicast().onBackpressureBuffer();

    private final ConcurrentHashMap<UUID, MySseEmitter> emitters = new ConcurrentHashMap<>();

    private void doSelectedUserIds(final Flux<String> userIds, final Function<MySseEmitter, UserServerSentEvent> consumer) {
        userIds.flatMap(userId -> Flux.fromIterable(emitters.values()).filter(emitter -> emitter.getUserId().equals(userId))).map(consumer).subscribe(manySinks::tryEmitNext);
    }

    private void doAll(final Function<MySseEmitter, UserServerSentEvent> consumer) {
        Flux.fromIterable(emitters.values()).map(consumer).subscribe(manySinks::tryEmitNext);
    }

    private final ExecutorService sseMvcExecutor = Executors.newSingleThreadExecutor();

    @Scheduled(fixedDelay = 1000 * 60, initialDelay = 1000 * 55)
    public void pingAll() {
        doAll(MySseEmitter::sendPing);
    }

    @Scheduled(fixedDelay = 1000 * 15, initialDelay = 1000 * 60)
    public void pingUpdateStatusAll() {
        doAll(mySseEmitter -> pingUpdateStatus(mySseEmitter).block());
    }

    private Mono<UserServerSentEvent> pingUpdateStatus(final MySseEmitter mySseEmitter) {
        return userRepository.findById(mySseEmitter.getUserId())
            .map(User::getInvites)
            .map(list -> {

                userRepository.findIncomingInvites(mySseEmitter.getUserId()).map(User::getId).collectList().subscribe(list::retainAll);
                return list;
            })
            .map(list -> {
                list.retainAll(emitters.values().stream().map(MySseEmitter::getUserId).toList());
                return list;
            })
            .map(mySseEmitter::createOnlineEvent);
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
        manySinks.tryEmitComplete();
    }

    @Override
    public Integer size() {
        return emitters.size();
    }

    @Override
    public Flux<ServerSentEvent<Object>> subscribe(final String userId) {

        log.info("subscribe({})", userId);

        final MySseEmitter mySseEmitter = new MySseEmitter(userId);
        emitters.put(mySseEmitter.getUuid(), mySseEmitter);
        manySinks.tryEmitNext(mySseEmitter.sendPing());

        sseMvcExecutor.execute(() -> {
            for (int i = 0; i < 5; i++) {
                manySinks.tryEmitNext(mySseEmitter.sendPing());
                try {
                    Thread.sleep(1000 * i);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        return manySinks.asFlux()
            .filter(userServerSentEvent -> mySseEmitter.getUuid().equals(userServerSentEvent.getUuid()))
            .map(UserServerSentEvent::getServerSentEvent)
            .publish()
            .autoConnect();
    }

    @Override
    public void ping(final UUID uuid) {
        final long count = emitters.values()
            .stream()
            .filter(mySseEmitter -> mySseEmitter.getUuid().equals(uuid))
            .map(MySseEmitter::receivePing)
            .map(manySinks::tryEmitNext)
            .count();


        if (count == 0) {
            log.error("ping() Found no emitter for uuid {}", uuid);
        }
    }


    @Override
    public void pong(final UUID uuid) {
        final long count = emitters.values()
            .stream()
            .filter(mySseEmitter -> mySseEmitter.getUuid().equals(uuid))
            .map(mySseEmitter -> {
                mySseEmitter.receivePong();
                return "";
            })
            .count();

        if (count == 0) {
            log.error("pong() Found no emitter for uuid {}", uuid);
        }

    }

    @Override
    public Game gameChanged(final Game gameState) {
        doSelectedUserIds(Flux.fromIterable(gameState.getPlayers()), mySseEmitter -> mySseEmitter.gameChanged(gameState));
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
