package nl.appsource.cardserver.service;

import lombok.NonNull;
import nl.appsource.cardserver.model.Game;
import org.openapitools.model.UserMessage;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

public interface SseEventSender {

    Mono<Void> sendAppIdentifierMessage(String appIdentifier, UserMessage userMessage);

    Mono<Void> sendUserIdMessage(String userId, UserMessage userMessage);

    Mono<Void> sendUserIdMessage(Set<String> userIds, UserMessage userMessage);

    Mono<Void> sendPong(String id);

    Mono<Void> friendsChanged(Set<String> userIds);

    Mono<Void> gamesChanged(Set<String> userIds);

    Mono<Void> boomsChanged(Set<String> userIds);

    Mono<Void> newGame(Game game);

    Mono<Void> sendOnlineListTo(String userId, List<@NonNull String> onlineList);

    Mono<Void> sendOnlineListToFriendsOf(String userId);

    Mono<Void> sendOnlineListTo(String userId);
}
