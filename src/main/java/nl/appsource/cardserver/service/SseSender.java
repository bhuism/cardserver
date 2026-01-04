package nl.appsource.cardserver.service;

import org.openapitools.model.UserMessage;
import reactor.core.publisher.Mono;

import java.util.Collection;

public interface SseSender {

    Mono<Void> sendAppIdentifierMessage(String appIdentifier, UserMessage userMessage);

    Mono<Void> sendUserIdMessage(String userId, UserMessage userMessage);

    Mono<Void> sendUserIdMessage(Collection<String> userIds, UserMessage userMessage);

}
