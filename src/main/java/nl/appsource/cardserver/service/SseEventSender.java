package nl.appsource.cardserver.service;

import lombok.NonNull;
import org.openapitools.model.UserMessage;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

public interface SseEventSender {

    Mono<Void> sendAppIdentifierMessage(String appIdentifier, UserMessage userMessage);

    Mono<Void> sendUserIdMessage(String userId, UserMessage userMessage);

    Mono<Void> sendUserIdMessage(Collection<String> userIds, UserMessage userMessage);

    Mono<Void> sendPong(String id);

    Mono<Void> friendsChanged(Collection<String> userIds);

    Mono<Void> gamesChanged(Collection<String> userIds);

    Mono<Void> boomsChanged(Collection<String> userIds);

    Mono<Void> sendOnlineListTo(String userId, List<@NonNull String> onlineList);
}
