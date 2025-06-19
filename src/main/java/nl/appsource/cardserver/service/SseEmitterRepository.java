package nl.appsource.cardserver.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

public interface SseEmitterRepository {
    void sendMessage(String userId, String message);

    Integer size();

    SseEmitter subscribe(String userId);

    void ping(UUID uuid);

    void pong(UUID uuid);
}
