package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.GameToOpenApiConverter;
import nl.appsource.cardserver.filter.LoggingFilter;
import nl.appsource.cardserver.service.GameService;
import org.openapitools.api.GamesApi;
import org.openapitools.model.CreateGame;
import org.openapitools.model.Game;
import org.openapitools.model.PlayCard;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

import static nl.appsource.cardserver.converter.GameToOpenApiConverter.convertCard;

@RestController
@RequiredArgsConstructor
@Slf4j
public class GameController implements GamesApi, V1Api {

    private final GameService gameService;
    private final GameToOpenApiConverter gameToOpenApiConverter;

    @Override
    public ResponseEntity<Game> getGame(final String gameId) {
        LoggingFilter.requestLogMessage("getGame(" + gameId + ")");

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String userId = authentication.getName();

        return gameService.getGame(userId, gameId)
            .map(gameToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }


    @Override
    public ResponseEntity<Game> playCard(final String gameId, final PlayCard playCard) {

        LoggingFilter.requestLogMessage("playCard(" + playCard.getCard() + ")");

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String userId = authentication.getName();

        return gameService.getGame(userId, gameId)
            .map(g -> gameService.playCard(userId, g, convertCard(playCard.getCard())))
            .map(gameToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<List<Game>> getGames() {

        LoggingFilter.requestLogMessage("getGames()");

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String userId = authentication.getName();

        return
            ResponseEntity.ok(
                gameService.getGames(userId)
                    .stream()
                    .map(gameToOpenApiConverter::convert)
                    .collect(Collectors.toList()));

    }

    @Override
    public ResponseEntity<Game> createGame(final CreateGame createGame) {

        LoggingFilter.requestLogMessage("createGame()");

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String userId = authentication.getName();

        return ResponseEntity.ok(gameToOpenApiConverter.convert(gameService.createGame(userId, createGame.getPlayers())));
    }

    @Override
    public ResponseEntity<Void> deleteGame(final String gameId) {
        LoggingFilter.requestLogMessage("deleteGame(" + gameId + ")");
        gameService.deleteGame(gameId);
        return ResponseEntity.ok().build();
    }


}
