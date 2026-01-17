package nl.appsource.cardserver.service;

import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.service.event.ScheduledGameEvent;
import org.openapitools.model.AiRisc;
import org.openapitools.model.GameVariant;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface GameService {

    Mono<Game> getGame(String userId, String gameId);

    Flux<String> getGames(String userId, boolean includeBoom, boolean includeFinished, Integer limit);

    Mono<Game> createGame(String creator, List<String> players, GameVariant gameVariant, AiRisc aiRisc);

    Mono<Game> createGame(String creator, List<String> players, GameVariant gameVariant, String boomId, Integer dealer, AiRisc aiRisc);

    Mono<Boolean> deleteGame(String userId, String gameId);

//    Mono<PlayCardResponse> playCard(UUID appIdentifier, String userId, String gameId, Card card);

//    Mono<Void> say(UUID appIdentifier, String userId, String gameId, Boolean say);

//    Mono<Game> executeSynchronious(ScheduledGameEvent eventToExecute);

    void scheduleGameEvent(ScheduledGameEvent scheduledGameEvent);

    //Mono<Void> reload(String appIdentifier, String userId, String gameId);

    Mono<Void> claimRoem(String appIdentifier, String userId, String gameId);

    Mono<Void> claimVerzaken(String appIdentifier, String userId, String gameId);
}
