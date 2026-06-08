package nl.appsource.cardserver.api.service;

import nl.appsource.cardserver.model.AiRisc;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.model.GameVariant;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface GameService {

    Mono<Game> getGame(String userId, String gameId);

    Flux<String> getGames(String userId, boolean includeBoom, boolean includeFinished, Integer limit);

    Mono<Game> createGame(String creator, List<String> players, GameVariant gameVariant, AiRisc aiRisc);

    Mono<Game> createGame(String creator, List<String> players, GameVariant gameVariant, String boomId, Integer dealer, AiRisc aiRisc);

    Mono<Boolean> deleteGame(String userId, String gameId);

}
