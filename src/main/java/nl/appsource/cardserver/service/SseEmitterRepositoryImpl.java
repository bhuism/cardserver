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
import java.util.function.Function;

@Service
@Slf4j
@RequiredArgsConstructor
public class SseEmitterRepositoryImpl implements SseEmitterRepository {

    private final UserRepository userRepository;

    private final Sinks.Many<UserServerSentEvent> manySinks = Sinks.many().multicast().onBackpressureBuffer();

    private final ConcurrentHashMap<UUID, MySseEmitter> emitters = new ConcurrentHashMap<>();

    private void doSelected(final Flux<MySseEmitter> receivers, final Function<MySseEmitter, UserServerSentEvent> consumer) {
        receivers.map(consumer).subscribe(manySinks::tryEmitNext);
    }

    private void doSelectedMono(final Flux<MySseEmitter> receivers, final Function<MySseEmitter, Mono<UserServerSentEvent>> consumer) {
        receivers.flatMap(consumer).subscribe(manySinks::tryEmitNext);
    }

    private void doAll(final Function<MySseEmitter, UserServerSentEvent> consumer) {
        doSelected(Flux.fromIterable(emitters.values()), consumer);
    }

    private void doAllMono(final Function<MySseEmitter, Mono<UserServerSentEvent>> consumer) {
        doSelectedMono(Flux.fromIterable(emitters.values()), consumer);
    }

    @Scheduled(fixedDelay = 1000 * 60, initialDelay = 1000 * 60)
    public void pingAll() {
        doAll(MySseEmitter::sendPing);
    }

    @Scheduled(fixedDelay = 1000 * 15, initialDelay = 1000 * 60)
    public void pingUpdateStatusAll() {
        doAllMono(this::pingUpdateStatus);
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
            .map(mySseEmitter::sendOnline);
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

//    private Sinks.Many<ServerSentEvent<String>> testSink = Sinks.many().multicast().onBackpressureBuffer();


    @Override
    public Flux<ServerSentEvent<Object>> subscribe(final String userId) {

        log.info("subscribe({})", userId);

        //pingUpdateStatusAll();

        final MySseEmitter mySseEmitter = new MySseEmitter(userId);
        emitters.put(mySseEmitter.getUuid(), mySseEmitter);
        manySinks.tryEmitNext(mySseEmitter.sendPing());

        return manySinks.asFlux()
            .filter(userServerSentEvent -> mySseEmitter.getUuid().equals(userServerSentEvent.getUuid()))
            .map(UserServerSentEvent::getServerSentEvent)
            .publish()
            .autoConnect();
    }

//    @Scheduled(fixedDelay = 1000)
//    public void emit() {
//        doAll(MySseEmitter::sendPing);
//    }


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
        doSelected(Flux.fromIterable(emitters.values())
                .filter(e -> gameState.getPlayers().contains(e.getUserId())),
            mySseEmitter -> mySseEmitter.gameChanged(gameState));
        return gameState;
    }

    @Override
    public void friendsChanged(final Collection<String> userIds) {
        doSelected(Flux.fromIterable(emitters.values()).filter(emitter -> userIds.contains(emitter.getUserId())), MySseEmitter::sendUpdateFriends);
    }

    @Override
    public void gamesChanged(final Collection<String> userIds) {
        doSelected(Flux.fromIterable(emitters.values()).filter(emitter -> userIds.contains(emitter.getUserId())), MySseEmitter::sendUpdateGames);
    }

    @Override
    public boolean isUserOnline(final String userId) {
        return emitters.values().stream().anyMatch(emitter -> emitter.getUserId().equals(userId));
    }

}
