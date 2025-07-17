package nl.appsource.cardserver.service;

import nl.appsource.cardserver.model.Game;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.UUID;

public interface SseEmitterRepository {

    void sendOnlineListToFriendsOf(String userId);

    void broadCastMessage(String userId, String message);

    Flux<ServerSentEvent<Object>> subscribe(String userId);

    void ping(UUID uuid);

    void pong(UUID uuid);

    Game gameChanged(Game gameState);

    void friendsChanged(Collection<String> userIds);

    void gamesChanged(Collection<String> userIds);

    void newGame(Game game);

    boolean isUserOnline(String userId);

}
