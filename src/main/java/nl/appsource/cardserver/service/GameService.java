package nl.appsource.cardserver.service;

import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import org.openapitools.model.PlayCardResponse;
import org.openapitools.model.UserMessage;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

public interface GameService {

    Mono<Game> getGame(String userId, String gameId);

    Flux<Game> getGames(String userId);

    Mono<Game> createGame(String creator, Set<String> players);

    Mono<Void> deleteGame(String gameId);

    Mono<PlayCardResponse> playCard(String userId, String gameId, Card card);

    Mono<PlayCardResponse> playAiCard(String userId, String gameId);

    Game sendGameChangedEvent(Game game);

    void sendUserMessage(UserMessage userMessage);

    Flux<ServerSentEvent<?>> gameStream(String userId, String gameId);

}
