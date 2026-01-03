package nl.appsource.cardserver.service;

import lombok.NonNull;
import nl.appsource.cardserver.model.Boom;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.User;
import org.openapitools.model.UserMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

public interface SseEmitterRepository {

    void sendOnlineListToFriendsOf(String userId);

    void sendOnlineListTo(String userId);

    void sendMessage(Collection<String> userIds, UserMessage userMessage);

    void sendAppIdentifierMessage(UUID appIdentifier, UserMessage userMessage);

    Flux<@NonNull MyServerSentEvent> subscribe(UUID appIdentifier, String userId, String remoteAddress, String userAgent);

    Mono<Void> ping(UUID appIdentifier);

    Mono<Void> pong(UUID appIdentifier);

    void friendsChanged(Collection<String> userIds);

    void gamesChanged(Collection<String> userIds);

    void boomsChanged(Collection<String> userIds);

    void updateGame(Game game);

    void updateGameForId(UUID appIdentifier, Game game);

    void updateUserForId(UUID appIdentifier, User user);

    void updateUser(User user);

    void updateBoom(Boom boom);

    void newGame(Game game);

    Mono<Boolean> isUserOnline(String userId);

    void newFriend(String userId, String friendId);

    void reloadCache(UUID appIdentifier, String userId);

//    Boolean validate(UUID appIdentifier, String userId);

    SseEmitterRepositoryImpl.DebugSseConnections getDebugSseConnections();

    //Mono<User> validate(UUID appIdentifier, User user);

//    void eventSubscribe(UUID appIdentifier, Set<String> topic);

//    void eventUnSubscribe(UUID appIdentifier, Set<String> topic);

//    int getSubscriptionCount(String topic);
}
