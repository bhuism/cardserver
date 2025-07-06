package nl.appsource.cardserver.service;

import org.openapitools.model.Game;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.UUID;

public interface SseEmitterRepository {

    void broadCastMessage(String userId, String message);

    Flux<ServerSentEvent<Object>> subscribe(String userId);

    void ping(UUID uuid);

    void pong(UUID uuid);

    Game gameChanged(Game gameState);

    void friendsChanged(Collection<String> userIds);

    void gamesChanged(Collection<String> userIds);

    boolean isUserOnline(String userId);
}
