package nl.appsource.cardserver.service;

import lombok.NonNull;
import nl.appsource.cardserver.model.Boom;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.User;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

public interface SseEmitterRepository {

    void sendOnlineListToFriendsOf(String userId);

    void sendOnlineListTo(String userId);

    void send(MyServerSentEvent myServerSentEvent);

    Flux<@NonNull MyServerSentEvent> subscribe(String appIdentifier, String userId, String remoteAddress, String userAgent);

    void friendsChanged(Collection<String> userIds);

    void gamesChanged(Collection<String> userIds);

    void boomsChanged(Collection<String> userIds);


    void updateGame(Game game);

    void updateUser(User user);

    void updateBoom(Boom boom);


    void updateGameForId(String appIdentifier, Game game);

    void updateUserForId(String appIdentifier, User user);


    void newGame(Game game);

    void newFriend(String userId, String friendId);

    void reloadCache(String appIdentifier, String userId);

    Mono<Boolean> isUserOnline(String userId);

//    Boolean validate(UUID appIdentifier, String userId);

//    void send(SseEvent sseEvent);

    //Mono<User> validate(UUID appIdentifier, User user);

//    void eventSubscribe(UUID appIdentifier, Set<String> topic);

//    void eventUnSubscribe(UUID appIdentifier, Set<String> topic);

//    int getSubscriptionCount(String topic);
}
