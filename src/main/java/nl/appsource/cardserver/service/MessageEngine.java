package nl.appsource.cardserver.service;


import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
                final String totalMessage = name + ":" + message;
                log.info("sending {} to: {}", totalMessage, sseEmitter);
                sseEmitter.send(SseEmitter.event().id(UUID.randomUUID().toString()).reconnectTime(1000).name("cardservermessage").data(totalMessage).comment("test123").build());
            } catch (final Throwable e) {
                log.error("Something went wrong while sending message " + e.getClass().getName() + ":" + e.getMessage());
                emitters.remove(sseEmitter);
                try {
                    sseEmitter.completeWithError(e);
                } catch (final Throwable e1) {
                    log.error("", e1);
                }
            }
        });
    }

    public SseEmitter subscribe() {

        final SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitters.add(emitter);

        log.info("onSubscribe() Adding an emitter, size={}", emitters.size());

        emitter.onCompletion(() -> {
            log.info("onCompletion() Removing an emitier");
            emitters.remove(emitter);
        });
        emitter.onTimeout(() -> {
            log.info("onTimeout() Removing an emitier");
            emitter.complete();
            emitters.remove(emitter);
        });

        return emitter;

    }

    @PreDestroy
    public void preDestroy() {
        while (!emitters.isEmpty()) {
            try {
                emitters.removeFirst().complete();
            } catch (final Throwable e) {
                log.error("", e);
            }
        }
    }

}
