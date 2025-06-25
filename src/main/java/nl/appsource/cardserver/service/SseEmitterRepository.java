package nl.appsource.cardserver.service;

import nl.appsource.cardserver.model.Card;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collection;
import java.util.UUID;

public interface SseEmitterRepository {
    void sendMessage(String userId, String message);

    Integer size();

    SseEmitter subscribe(String userId);

    void ping(UUID uuid);

    void pong(UUID uuid);

    void playCard(String userId, String gameId, Card card);

    void friendsChanged(Collection<String> userId);
}
