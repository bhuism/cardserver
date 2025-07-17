package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.GameToOpenApiConverter;
import nl.appsource.cardserver.filter.LoggingFilter;
import nl.appsource.cardserver.service.GameService;
import nl.appsource.cardserver.service.exception.GameEngineException;
import org.openapitools.api.GamesApi;
import org.openapitools.model.Both;
import org.openapitools.model.CreateGame;
import org.openapitools.model.Game;
import org.openapitools.model.PlayCard;
import org.openapitools.model.UserMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static nl.appsource.cardserver.converter.GameToOpenApiConverter.convertCard;

@RestController
@RequiredArgsConstructor
@Slf4j
public class GameController implements GamesApi, V1Api {

    private final GameService gameService;
    private final GameToOpenApiConverter gameToOpenApiConverter;

    @Override
    public Mono<ResponseEntity<Game>> getGame(final String gameId, final ServerWebExchange exchange) {
        LoggingFilter.requestLogMessage("getGame(" + gameId + ")");

        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .flatMap(userId -> gameService.getGame(userId, gameId))
            .mapNotNull(gameToOpenApiConverter::convert)
            .map(ResponseEntity::ok);
    }


    //    @Override
    public Mono<ResponseEntity<Both>> playCard(final String gameId, final Mono<PlayCard> playCardMono, final ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .flatMap(userId -> playCardMono.map(playCard -> {
                LoggingFilter.requestLogMessage("playCard(" + playCard.getCard() + ")");
                return playCard;
            }).flatMap(playCard -> gameService.playCard(userId, gameId, convertCard(playCard.getCard()))
                .mapNotNull(gameToOpenApiConverter::convert)
                .map(Both.class::cast)
                .onErrorResume(GameEngineException.class, throwable -> Mono.just(new UserMessage(throwable.getMessage(), throwable.getVariant())))
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.unprocessableEntity().build())
            ));
    }

    @Override
    public Mono<ResponseEntity<Flux<Game>>> getGames(final ServerWebExchange exchange) {

        LoggingFilter.requestLogMessage("getGames()");

        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .mapNotNull(userId -> gameService.getGames(userId).mapNotNull(gameToOpenApiConverter::convert))
            .map(ResponseEntity::ok);

    }

    @Override
    public Mono<ResponseEntity<Game>> createGame(final Mono<CreateGame> createGameMono, final ServerWebExchange exchange) {

        LoggingFilter.requestLogMessage("createGame()");

        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .flatMap(userId -> createGameMono.flatMap(createGame -> gameService.createGame(userId, createGame.getPlayers())))
            .mapNotNull(gameToOpenApiConverter::convert)
            .map(ResponseEntity::ok);

    }

    @Override
    public Mono<ResponseEntity<Void>> deleteGame(final String gameId, final ServerWebExchange exchange) {
        LoggingFilter.requestLogMessage("deleteGame(" + gameId + ")");
        return gameService.deleteGame(gameId)
            .then(Mono.just(ResponseEntity.ok().build()));
    }


}
