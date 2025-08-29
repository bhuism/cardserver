package nl.appsource.cardserver.service;

import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import org.openapitools.model.PlayCardResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Set;

public interface GameService {

    Mono<Game> getGame(String userId, String gameId);

    Flux<Game> getGames(String userId);

    Mono<Game> createGame(String creator, Set<String> players);

    Mono<Void> deleteGame(String gameId);

    Mono<PlayCardResponse> playCard(String userId, String gameId, Card card);

    void finishWithAi(String gameId, Duration initialDelay);

    Mono<Void> say(String userId, String gameId, Boolean say);

    Mono<Void> openLastTrick(String userId, String gameId);
}
