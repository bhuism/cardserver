package nl.appsource.cardserver.service;

import org.openapitools.model.Game;

import java.util.Optional;
import java.util.Set;

public interface GameService {

    Optional<Game> findById(String gameId);

    Set<String> findByCreator(String creator);
}
