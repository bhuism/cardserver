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

    Mono<Void> kickAi(String userId, String gameId);

    void sendGameChangedEvent(Game game);

    void sendUserMessage(UserMessage userMessage);

    Flux<ServerSentEvent<Object>> gameStream(String userId, String gameId, String remoteAddress);

    Mono<Void> say(String userId, String gameId, Boolean say);
}
