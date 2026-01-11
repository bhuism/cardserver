package nl.appsource.cardserver.service;

import lombok.NonNull;
import nl.appsource.cardserver.model.Boom;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.User;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SseEmitterRepository {

    Mono<Void> sendOnlineListToFriendsOf(String userId);

    Mono<Void> sendOnlineListTo(String userId);

    Mono<@NonNull MyServerSentEvent> createOnlineListForUser(String userId);

//    void send(MyServerSentEvent myServerSentEvent);
    void send(String appIdentifier, String userId, MyServerSentEvent myServerSentEvent);

    Flux<@NonNull MyServerSentEvent> subscribe(String appIdentifier, String userId, String remoteAddress, String userAgent);

    void updateGame(Game game);

    void updateUser(User user);

    void updateBoom(Boom boom);

    // void updateGameForId(String appIdentifier, Game game);

    Mono<Game> newGame(Game game);

    void reloadCache(String appIdentifier, String userId);

    //Mono<Boolean> isUserOnline(String userId);

}
