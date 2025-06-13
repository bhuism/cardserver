package nl.appsource.cardserver.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class SseEmitterRepository {

    private final CopyOnWriteArrayList<MySseEmitter> emitters = new CopyOnWriteArrayList<>();

    public void send(final User fromUser, final String message, final UserRepository userRepository) {

        emitters.forEach(mySseEmitter -> {
            mySseEmitter.send(fromUser, message, emitters::remove);
        });
    }

    @PreDestroy
    public void destroy() {
        while (!emitters.isEmpty()) {

            emitters.forEach(MySseEmitter::complete);

            try {
                emitters.clear();
            } catch (final Throwable e) {
                log.error("", e);
            }
        }

    }

    public Integer size() {
        return emitters.size();
    }

    public SseEmitter subscribe(final String userId) {
        final MySseEmitter mySseEmitter = new MySseEmitter(userId, emitters::remove);
        emitters.add(mySseEmitter);
        return mySseEmitter.getEmitter();
    }

}
