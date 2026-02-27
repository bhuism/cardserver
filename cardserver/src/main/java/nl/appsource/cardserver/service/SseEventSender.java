package nl.appsource.cardserver.service;

import lombok.NonNull;
import nl.appsource.cardserver.couchbase.model.Game;
import nl.appsource.generated.openapi.model.UserMessage;
import reactor.core.publisher.Mono;

import java.util.Set;

public interface SseEventSender {

    // Mono<Void> sendAppIdentifierMessage(String appIdentifier, UserMessage userMessage);

    Mono<Void> sendUserIdMessage(String userId, String message, UserMessage.VariantEnum variant);

    Mono<Void> sendUserIdsMessage(Set<String> userIds, String message, UserMessage.VariantEnum variant);

    Mono<Void> sendPong(String id);

    Mono<Void> friendsChanged(Set<String> userIds);

    Mono<Void> gamesChanged(Set<String> userIds);

    Mono<Void> boomsChanged(Set<String> userIds);

    Mono<Void> newGame(Game game);

    Mono<Void> sendOnlineListTo(String userId, Set<@NonNull String> onlineList);

    Mono<Void> sendOnlineListToFriendsOf(String userId);

    Mono<Void> sendOnlineListTo(String userId);
}
