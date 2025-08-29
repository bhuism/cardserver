package nl.appsource.cardserver.service;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SseEmitterRepositoryImpl implements SseEmitterRepository {

    private final UserRepository userRepository;

    private final ConcurrentHashMap<String, MySseEmitter> emitters = new ConcurrentHashMap<>();

    private final GameToOpenApiConverter gameToOpenApiConverter;

    private void doSelectedUserIds(final Collection<String> userIds, final Consumer<MySseEmitter> consumer) {
        emitters.entrySet().stream().filter(stringMySseEmitterEntry -> userIds.contains(stringMySseEmitterEntry.getKey())).map(Map.Entry::getValue).forEach(consumer);
    }

    private void doUserId(final String userId, final Consumer<MySseEmitter> consumer) {
        Optional.ofNullable(emitters.get(userId)).ifPresent(consumer);
    }

    private void doAll(final Consumer<MySseEmitter> consumer) {
        Flux.fromIterable(emitters.values()).subscribe(consumer);
    }

    @Scheduled(fixedDelay = 1000 * 15, initialDelay = 1000 * 30)
    public void pingAll() {
        doAll(MySseEmitter::sendPing);
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
        doUserId(userId, mySseEmitter -> mySseEmitter.sendOnlineList(getFriends(userId).filter(this::isUserOnline)));
    }

    @Override
    public void broadCastMessage(final String userId, final String message) {
        userRepository.findById(userId)
            .map(User::getDisplayName)
            .switchIfEmpty(Mono.just(userId))
            .subscribe(fromString -> doAll(mySseEmitter -> mySseEmitter.message(new UserMessage().userId(userId).message(fromString + ": " + message))));
    }

    @Override
    public void ping(final String userId) {
        doUserId(userId, MySseEmitter::receivePing);
    }

    @Override
    public void pong(final String userId) {
        doUserId(userId, MySseEmitter::receivePong);
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
        doSelectedUserIds(game.getPlayers(), mySseEmitter -> mySseEmitter.sendUpdateGameState(convertedGame));
    }

    @Override
    public void newGame(final Game game) {
        doSelectedUserIds(game.getPlayers().stream().filter(player -> !player.equals(game.getCreator())).collect(Collectors.toSet()), mySseEmitter -> mySseEmitter.newGame(Objects.requireNonNull(gameToOpenApiConverter.convert(game))));
    }

    @Override
    public boolean isUserOnline(final String userId) {
        return emitters.containsKey(userId);
    }

    @Override
    public void newFriend(final String userId, final String friendId) {
        doUserId(userId, mySseEmitter -> mySseEmitter.newFriend(friendId));
    }


    @Override
    public void sendUserMessage(final List<String> receivers, final UserMessage userMessage) {
        doSelectedUserIds(receivers, mySseEmitter -> mySseEmitter.message(userMessage));
    }

    @Override
    public Flux<ServerSentEvent<Object>> subscribe(final String userId, final String remoteAddress) {

        final MySseEmitter mySseEmitter = new MySseEmitter();

        emitters.put(userId, mySseEmitter);

        return Flux.just(mySseEmitter.createPingEvent().serverSentEvent(), mySseEmitter.createPingEvent().serverSentEvent(), mySseEmitter.createPingEvent().serverSentEvent())
            .concatWith(mySseEmitter.subscribe())
            .doOnSubscribe((s) -> {
                log.info("{} subscribe() userId={} count={}", remoteAddress, userId, emitters.size());
                sendOnlineListTo(userId);
                sendOnlineListToFriendsOf(userId);
            })
            .doFinally((_s) -> {
                log.info("{} unSubscribe() userId={}, count={}", remoteAddress, userId, emitters.size());
                emitters.remove(userId);
//                mySseEmitter.cancel();
//                janitor();
                sendOnlineListToFriendsOf(userId);
//                try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
//                    executor.submit(() -> {
//                        try {
//                            Thread.sleep(1000);
//                            sendOnlineListToFriendsOf(userId);
//                        } catch (final InterruptedException e) {
//                            log.error("", e);
//                        }
//                    });
//                    executor.shutdown();
//                }
            });
    }

}
