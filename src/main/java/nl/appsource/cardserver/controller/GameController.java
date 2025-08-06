package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.GameToOpenApiConverter;
import nl.appsource.cardserver.service.GameService;
import nl.appsource.cardserver.service.exception.GameEngineException;
import org.openapitools.api.GamesApi;
import org.openapitools.model.CreateGame;
import org.openapitools.model.Game;
import org.openapitools.model.PlayCard;
import org.openapitools.model.PlayCardResponse;
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
import static reactor.core.publisher.Mono.just;

@RestController
@RequiredArgsConstructor
@Slf4j
public class GameController implements GamesApi, V1Api {

    private final GameService gameService;

    private final GameToOpenApiConverter gameToOpenApiConverter;

    @Override
    public Mono<ResponseEntity<Game>> getGame(final String gameId, final ServerWebExchange exchange) {

        log.info("{} getGame({})", exchange.getRequest().getRemoteAddress(), gameId);

        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .flatMap(userId -> gameService.getGame(userId, gameId))
            .mapNotNull(gameToOpenApiConverter::convert)
            .map(ResponseEntity::ok);
    }


    @Override
    public Mono<ResponseEntity<PlayCardResponse>> playCard(final String gameId, final Mono<PlayCard> playCardMono, final ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .flatMap(userId -> playCardMono.map(playCard -> {
                    log.info("{} playCard() user {} plays card {}", exchange.getRequest().getRemoteAddress(), userId, playCard.getCard());
                    return playCard;
                })
                .flatMap(playCard -> gameService.playCard(userId, gameId, convertCard(playCard.getCard()))))
            .onErrorResume(GameEngineException.class, throwable -> {
                gameService.sendUserMessage(new UserMessage().message(throwable.getMessage()).variant(throwable.getVariant()));
                return just(new PlayCardResponse().cardWasPlayed(false));
            })
            .onErrorResume(Throwable.class, throwable -> {
                log.error("", throwable);
                gameService.sendUserMessage(new UserMessage().message(throwable.getClass().getName() + ":" + throwable.getMessage()).variant(UserMessage.VariantEnum.ERROR));
                return just(new PlayCardResponse().cardWasPlayed(false));
            })
            .map(ResponseEntity::ok);
    }


    @Override
    public Mono<ResponseEntity<Void>> playAiCard(final String gameId, final ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .map(userId -> {
                log.info("{} playAiCard() {}", exchange.getRequest().getRemoteAddress(), userId);
                return userId;
            })
            .flatMap(userId -> gameService.playAiCard(userId, gameId))
            .onErrorResume(GameEngineException.class, throwable -> {
                gameService.sendUserMessage(new UserMessage().message(throwable.getMessage()).variant(throwable.getVariant()));
                return Mono.justOrEmpty(true).then();
            })
            .onErrorResume(Throwable.class, throwable -> {
                log.error("", throwable);
                gameService.sendUserMessage(new UserMessage().message(throwable.getClass().getName() + ":" + throwable.getMessage()).variant(UserMessage.VariantEnum.ERROR));
                return Mono.justOrEmpty(true).then();
            })
            .then(just(ResponseEntity.ok().build()));
    }


    @Override
    public Mono<ResponseEntity<Flux<Game>>> getGames(final ServerWebExchange exchange) {

        log.info("{} getGames()", exchange.getRequest().getRemoteAddress());

        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .mapNotNull(userId -> gameService.getGames(userId).mapNotNull(gameToOpenApiConverter::convert))
            .map(ResponseEntity::ok);

    }

    @Override
    public Mono<ResponseEntity<Game>> createGame(final Mono<CreateGame> createGameMono, final ServerWebExchange exchange) {

        log.info("{} createGame()", exchange.getRequest().getRemoteAddress());

        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .flatMap(userId -> createGameMono.flatMap(createGame -> gameService.createGame(userId, createGame.getPlayers())))
            .mapNotNull(gameToOpenApiConverter::convert)
            .map(ResponseEntity::ok);

    }

    @Override
    public Mono<ResponseEntity<Void>> deleteGame(final String gameId, final ServerWebExchange exchange) {
        log.info("{} deleteGame({})", gameId, exchange.getRequest().getRemoteAddress());
        return gameService.deleteGame(gameId)
            .then(just(ResponseEntity.ok().build()));
    }


}
