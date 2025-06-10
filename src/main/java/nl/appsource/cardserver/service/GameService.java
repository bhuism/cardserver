package nl.appsource.cardserver.service;

import org.openapitools.model.Game;
import org.openapitools.model.PlayCard;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface GameService {

    Optional<Game> getGame(String userId, String gameId);

    List<Game> getGames(String userId);

    Game createGame(String creator, Set<String> players);

    void deleteGame(String gameId);

    ResponseEntity<Game> playCard(String userId, String gameId, PlayCard playCard);
}
