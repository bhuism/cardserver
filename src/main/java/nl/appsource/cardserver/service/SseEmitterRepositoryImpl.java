package nl.appsource.cardserver.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.GameRepository;
import nl.appsource.cardserver.repository.UserRepository;
import org.openapitools.model.Game;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SseEmitterRepositoryImpl implements SseEmitterRepository {

    private final UserRepository userRepository;

    private final GameRepository gameRepository;

    private final CopyOnWriteArraySet<MySseEmitter> emitters = new CopyOnWriteArraySet<>();

    private void doSelected(final Set<MySseEmitter> receivers, final Function<MySseEmitter, Boolean> consumer) {

        final Set<MySseEmitter> removers = new HashSet<>();

        receivers.forEach(mySseEmitter -> {
            if (!consumer.apply(mySseEmitter)) {
                removers.add(mySseEmitter);
            }
        });

        emitters.removeAll(removers);

    }


    private void doAll(final Function<MySseEmitter, Boolean> consumer) {
        doSelected(emitters, consumer);
    }

    @Scheduled(fixedDelay = 1000 * 60, initialDelay = 1000 * 60)
    public void pingAll() {
        doAll(MySseEmitter::sendPing);
    }

    @Scheduled(fixedDelay = 1000 * 15, initialDelay = 1000 * 60)
    public void pingUpdateStatusAll() {
        doAll(this::pingUpdateStatus);
    }

    public boolean pingUpdateStatus(final MySseEmitter mySseEmitter) {
        final List<String> incomingInvites = userRepository.findIncomingInvites(mySseEmitter.getUserId()).stream().map(User::getId).toList();
        return userRepository.findById(mySseEmitter.getUserId())
            .map(User::getInvites)
            .map(friends -> {
                friends.retainAll(incomingInvites);
                friends.retainAll(emitters.stream().map(MySseEmitter::getUserId).collect(Collectors.toList()));
//                log.info("Sending {} online list:  {}", mySseEmitter.getUserId(), friends);
                return mySseEmitter.sendOnline(friends);
            }).orElse(false);
    }

    @Override
    public void sendMessage(final String userId, final String message) {
        final String fromString = userRepository.findById(userId).map(User::getDisplayName).orElse(userId);
        doAll(mySseEmitter -> mySseEmitter.sendCardServerMessage(fromString, message));
    }

    @PreDestroy
    public void destroy() {

        emitters.forEach(MySseEmitter::complete);

        try {
            emitters.clear();
        } catch (final Throwable e) {
            log.error("", e);
        }
    }

    @Override
    public Integer size() {
        return emitters.size();
    }

    @Override
    public SseEmitter subscribe(final String userId) {
        final MySseEmitter mySseEmitter = new MySseEmitter(userId);

        // ping new connection
        if (!mySseEmitter.sendPing()) {
            throw new RuntimeException("ping error");
        }

        emitters.add(mySseEmitter);

        pingUpdateStatusAll();

        return mySseEmitter.getEmitter();
    }

    @Override
    public void ping(final UUID uuid) {
        doSelected(emitters.stream().filter(mySseEmitter -> mySseEmitter.getUuid().equals(uuid)).collect(Collectors.toSet()), mySseEmitter -> {
            pingUpdateStatus(mySseEmitter);
            return mySseEmitter.ping();
        });
    }

    @Override
    public void pong(final UUID uuid) {
        doSelected(emitters.stream().filter(mySseEmitter -> mySseEmitter.getUuid().equals(uuid)).collect(Collectors.toSet()), MySseEmitter::pong);
    }

    @Override
    public void gameChanged(final Game gameState) {
        final Set<MySseEmitter> players = emitters
            .stream()
            .filter(e -> gameState.getPlayers().stream().map(org.openapitools.model.User::getId).toList().contains(e.getUserId()))
            .collect(Collectors.toSet());

        doSelected(players, (emitter) -> emitter.gameChanged(gameState));
    }

    @Override
    public void friendsChanged(final Collection<String> userIds) {
        doSelected(emitters.stream().filter(emitter -> userIds.contains(emitter.getUserId())).collect(Collectors.toSet()), MySseEmitter::sendUpdateFriends);
    }

    @Override
    public void gamesChanged(final Collection<String> userIds) {
        doSelected(emitters.stream().filter(emitter -> userIds.contains(emitter.getUserId())).collect(Collectors.toSet()), MySseEmitter::sendUpdateGames);
    }

}
