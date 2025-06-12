package nl.appsource.cardserver.service;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageEngine {

    private final UserRepository userRepository;

    @Getter
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public void message(final String userId, final String message) {

        final String name = userRepository.findById(userId).map(User::getDisplayName).orElse("unknown");

        emitters.forEach(sseEmitter -> {
            try {
                sseEmitter.send(SseEmitter.event().id(UUID.randomUUID().toString()).name(name + ": " + "message").data(message).build());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public SseEmitter subscribe() {

        final SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);

        emitter.onCompletion(() -> {
            log.info("onCompletion() Removing an emitier");
            emitters.remove(emitter);
        });
        emitter.onTimeout(() -> {
            log.info("onTimeout() Removing an emitier");
            emitters.remove(emitter);
        });
        return emitter;

    }
}
