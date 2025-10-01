package nl.appsource.cardserver.service;

import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.service.event.ScheduledGameEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

public interface GameService {

    Mono<Game> getGame(String userId, String gameId);

    Flux<Game> getGames(String userId);

    Mono<Game> createGame(String creator, Set<String> players);

    Mono<Boolean> deleteGame(String userId, String gameId);

//    Mono<PlayCardResponse> playCard(UUID appIdentifier, String userId, String gameId, Card card);

//    Mono<Void> say(UUID appIdentifier, String userId, String gameId, Boolean say);

//    Mono<Game> executeSynchronious(ScheduledGameEvent eventToExecute);

    void scheduleGameEvent(ScheduledGameEvent scheduledGameEvent);

    Mono<Void> reload(UUID appIdentifier, String userId, String gameId);

    Mono<Void> claimRoem(UUID appIdentifier, String userId, String gameId);

    Mono<Void> gameMessage(String userId, String gameId, String message);
}
