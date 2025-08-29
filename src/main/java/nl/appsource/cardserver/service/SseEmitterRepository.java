package nl.appsource.cardserver.service;

import nl.appsource.cardserver.model.Game;
import org.openapitools.model.UserMessage;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SseEmitterRepository {

    void sendOnlineListToFriendsOf(String userId);

    void sendOnlineListTo(String userId);

    void broadCastMessage(String userId, String message);

    void sendUserMessage(List<String> receivers, UserMessage userMessage);

    Flux<ServerSentEvent<Object>> subscribe(String userId, String remoteAddress);

    void ping(String userId);

    void pong(String userId);

//    Game gameChanged(Game gameState);

    void friendsChanged(Collection<String> userIds);

    void gamesChanged(Collection<String> userIds);

    void updateGameState(Game game);

    void newGame(Game game);

    boolean isUserOnline(String userId);

    void newFriend(String userId, String friendId);

}
