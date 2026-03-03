package nl.appsource.cardserver.service;

import lombok.NonNull;
import nl.appsource.cardserver.model.Game;
import nl.appsource.generated.openapi.model.UserMessage;
import reactor.core.publisher.Mono;

import java.util.Set;

public interface SseEventSender {

    Mono<Void> sendUserIdMessage(String to, String from, String message, UserMessage.VariantEnum variant);

    Mono<Void> sendUserIdsMessage(Set<String> userIds, String from, String message, UserMessage.VariantEnum variant);

    Mono<Void> friendsChanged(Set<String> userIds);

    Mono<Void> gamesChanged(Set<String> userIds);

    Mono<Void> boomsChanged(Set<String> userIds);

    Mono<Void> newGame(Game game);

    Mono<Void> sendOnlineListTo(String userId, Set<@NonNull String> onlineList);

}
