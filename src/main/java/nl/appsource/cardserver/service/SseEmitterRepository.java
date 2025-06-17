package nl.appsource.cardserver.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SseEmitterRepository {

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


    @Scheduled(fixedRate = 1000 * 15, initialDelay = 1000 * 60)
    public void pingAll() {
        doAll(MySseEmitter::ping);
    }

    public void send(final String fromString, final String message) {
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

    public Integer size() {
        return emitters.size();
    }

    public SseEmitter subscribe(final String userId) {
        final MySseEmitter mySseEmitter = new MySseEmitter(userId);

        // ping new connection
        if (!mySseEmitter.ping()) {
            throw new RuntimeException("ping error");
        }

        emitters.add(mySseEmitter);
        return mySseEmitter.getEmitter();
    }

    public void pong(final String userId) {
        doSelected(emitters.stream().filter(mySseEmitter -> Objects.equals(mySseEmitter.getUserId(), userId)).collect(Collectors.toSet()), MySseEmitter::pong);
    }
}
