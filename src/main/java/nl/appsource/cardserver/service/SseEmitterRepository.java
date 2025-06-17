package nl.appsource.cardserver.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class SseEmitterRepository {

    private final CopyOnWriteArrayList<MySseEmitter> emitters = new CopyOnWriteArrayList<>();

    @Scheduled(fixedRate = 1000 * 15, initialDelay = 1000 * 60)
    public void pingAll() {

        final Set<MySseEmitter> removers = new HashSet<>();

        emitters.forEach(mySseEmitter -> {
            if (!mySseEmitter.sendPing()) {
                removers.add(mySseEmitter);
            }
        });

        emitters.removeAll(removers);

    }

    public void send(final String fromString, final String message) {

        final Set<MySseEmitter> removers = new HashSet<>();

        emitters.forEach(mySseEmitter -> {
            if (!mySseEmitter.sendCardServerMessage(fromString, message)) {
                removers.add(mySseEmitter);
            }
        });

        emitters.removeAll(removers);
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
        if (!mySseEmitter.sendPing()) {
            throw new RuntimeException("ping error");
        }

        emitters.add(mySseEmitter);
        return mySseEmitter.getEmitter();
    }

}
