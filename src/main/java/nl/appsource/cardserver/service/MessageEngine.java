package nl.appsource.cardserver.service;


import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageEngine {

    private final UserRepository userRepository;

    @Getter
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public void message(final String source, final String message) {


        final String sourceName = userRepository.findById(source).map(User::getDisplayName).orElse("unknown");


        emitters.entrySet().forEach(entry -> {

            final String sink = entry.getKey();

            final String sinkName = userRepository.findById(sink).map(User::getDisplayName).orElse("unknown");


            final SseEmitter sseEmitter = entry.getValue();

            try {
                log.info("sending {} from: {} to: {}", message, sourceName, sinkName);
                sseEmitter.send(SseEmitter.event().id(UUID.randomUUID().toString()).reconnectTime(1000).name("cardservermessage").data(sourceName + ":" + message).build());
            } catch (final Throwable e) {
                log.error("{}: from {} to {} message {}", e.getClass().getName() + ":" + e.getMessage(), sourceName, sink, message);
                emitters.remove(sseEmitter);
                try {
                    sseEmitter.complete();
                } catch (final Throwable e1) {
                    log.error("", e1);
                }
            }
        });
    }

    public SseEmitter subscribe(final String userId) {

        final SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitters.put(userId, emitter);

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
                emitters.clear();
            } catch (final Throwable e) {
                log.error("", e);
            }
        }
    }

}
