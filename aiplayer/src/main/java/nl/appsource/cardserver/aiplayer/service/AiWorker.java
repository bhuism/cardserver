package nl.appsource.cardserver.aiplayer.service;

import reactor.core.publisher.Mono;

public interface AiWorker {
    Mono<Void> say(String gameId, String userId);

    Mono<Void> playCard(String gameId, String userId);
}
