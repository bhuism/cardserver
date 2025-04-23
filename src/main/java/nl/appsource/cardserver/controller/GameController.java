package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class GameController implements GameApi, GamesApi {

    private final GameService gameService;

    @Override
    public ResponseEntity<Game> getGame(final String gameId) {

        final long start = System.currentTimeMillis();
        try {

            return gameService.findById(gameId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());

        } finally {
            log.info("getGame() gameID={} took {} ms", gameId, System.currentTimeMillis() - start);
        }
    }

    @Override
    public ResponseEntity<List<Game>> getGames() {
        final long start = System.currentTimeMillis();
        try {
            return new ResponseEntity<>(gameService.findAll(), HttpStatus.OK);
        } finally {
            log.info("getGames() took {} ms", System.currentTimeMillis() - start);
        }

    }


}
