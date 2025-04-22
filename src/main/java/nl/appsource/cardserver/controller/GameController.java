package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.service.GameService;
import org.openapitools.api.GameApi;
import org.openapitools.api.GamesApi;
import org.openapitools.model.Game;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GameController implements GameApi, GamesApi {

    private final GameService gameService;

    @Override
    public ResponseEntity<Game> getGame(final String gameId) {
        return gameService.findById(gameId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<List<Game>> getGames() {
        return new ResponseEntity<>(gameService.findAll(), HttpStatus.OK);
    }


}
