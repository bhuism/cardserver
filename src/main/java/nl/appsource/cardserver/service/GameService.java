package nl.appsource.cardserver.service;

import org.openapitools.model.Game;

import java.util.List;
import java.util.Optional;

public interface GameService {

    Optional<Game> findById(String gameId);

    List<Game> findAll();
}
