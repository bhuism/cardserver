package nl.appsource.cardserver.service;

import org.openapitools.model.Game;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface GameService {

    Optional<Game> findById(String gameId);

    List<Game> findByCreator(String creator);

    Game createGame(String creator, Set<String> players);
}
