package nl.appsource.cardserver.service;

import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import org.reactivestreams.Publisher;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

public interface GameService {

    Mono<Game> getGame(String userId, String gameId);

    Flux<Game> getGames(String userId);

    Mono<Game> createGame(String creator, Set<String> players);

    Mono<Void> deleteGame(String gameId);

    Mono<Game> playCard(String userId, String gameId, Card card);

    Publisher<? extends ServerSentEvent<org.openapitools.model.Game>> gameStream(String userId, String gameId);
}
