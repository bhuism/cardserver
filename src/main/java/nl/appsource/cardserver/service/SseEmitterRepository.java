package nl.appsource.cardserver.service;

import org.openapitools.model.Game;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collection;
import java.util.UUID;

public interface SseEmitterRepository {
    void sendMessage(String userId, String message);

    Integer size();

    SseEmitter subscribe(String userId);

    void ping(UUID uuid);

    void pong(UUID uuid);

    void gameChanged(Game gameState);

    void friendsChanged(Collection<String> userIds);

    void gamesChanged(Collection<String> userIds);
}
