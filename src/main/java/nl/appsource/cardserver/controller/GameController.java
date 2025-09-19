package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.GameToOpenApiConverter;
import nl.appsource.cardserver.repository.GameRepository;
import nl.appsource.cardserver.service.GameService;
import nl.appsource.cardserver.service.SseEmitterRepository;
import org.openapitools.api.GamesApi;
import org.openapitools.model.CreateGame;
import org.openapitools.model.Game;
import org.openapitools.model.PlayCard;
import org.openapitools.model.PlayCardResponse;
import org.openapitools.model.PlayerSay;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

import static nl.appsource.cardserver.converter.GameToOpenApiConverter.convertCard;
import static reactor.core.publisher.Mono.just;

@RestController
@RequiredArgsConstructor
@Slf4j
public class GameController implements GamesApi, V1Api {

    private final GameService gameService;

    private final GameToOpenApiConverter gameToOpenApiConverter;

    private final SseEmitterRepository sseEmitterRepository;
    private final GameRepository gameRepository;

    private Mono<String> getUserId(final ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("{} no authentication", exchange.getRequest()
                    .getRemoteAddress());
                return Mono.empty();
            }));
    }

    private Mono<String> authorize(final UUID appIdentifier, final ServerWebExchange exchange) {
        return getUserId(exchange)
            .filter((userId) -> sseEmitterRepository.validate(appIdentifier, userId))
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("{} sseEmitterRepository validation failed", exchange.getRequest()
                    .getRemoteAddress());
                return Mono.empty();
            }));
    }

    @Override
    public Mono<ResponseEntity<Game>> getGame(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} getGame()  userId={} gameId={}", exchange.getRequest()
                .getRemoteAddress(), userId, gameId))
            .flatMap(userId -> gameService.getGame(userId, gameId))
            .mapNotNull(gameToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("{} getGame({}), game not found", exchange.getRequest()
                    .getRemoteAddress(), gameId);
                return Mono.empty();
            }))
            .defaultIfEmpty(ResponseEntity.notFound()
                .build());
    }

    @Override
    public Mono<ResponseEntity<PlayCardResponse>> playCard(final UUID appIdentifier, final String gameId, final Mono<PlayCard> playCardMono, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} playCard()  userId={} gameId={}", exchange.getRequest()
                .getRemoteAddress(), userId, gameId))
            .flatMap(userId -> playCardMono.flatMap(playCard -> gameService.playCard(appIdentifier, userId, gameId, convertCard(playCard.getCard()))))
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound()
                .build());
    }

    @Override
    public Mono<ResponseEntity<Void>> kickAi(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} kickAi()  userId={} gameId={}", exchange.getRequest()
                .getRemoteAddress(), userId, gameId))
            .flatMap((userId) -> gameRepository.findByUserIdAndGameId(userId, gameId))
            .doOnNext(game -> gameService.finishWithAi(game.getId(), Duration.ZERO, game.getTurns()
                .size()))
            .then(just(ResponseEntity.ok()
                .build()));
    }

    @Override
    public Mono<ResponseEntity<Flux<Game>>> getGames(final UUID appIdentifier, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} getGames()  userId={}", exchange.getRequest()
                .getRemoteAddress(), userId))
            .mapNotNull(userId -> gameService.getGames(userId)
                .mapNotNull(gameToOpenApiConverter::convert))
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound()
                .build());

    }

    @Override
    public Mono<ResponseEntity<Game>> createGame(final UUID appIdentifier, final Mono<CreateGame> createGameMono, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} createGame()  userId={}", exchange.getRequest()
                .getRemoteAddress(), userId))
            .flatMap(userId -> createGameMono.flatMap(createGame -> gameService.createGame(userId, createGame.getPlayers())))
            .mapNotNull(gameToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound()
                .build());

    }

    @Override
    public Mono<ResponseEntity<Void>> deleteGame(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} deleteGame()  userId={} gameId={}", exchange.getRequest()
                .getRemoteAddress(), userId, gameId))
            .flatMap(userId -> gameService.deleteGame(userId, gameId)
                .defaultIfEmpty(false))
            .map(deleted -> deleted
                ? ResponseEntity.noContent()
                .<Void>build()
                : ResponseEntity.notFound()
                .<Void>build())
            .defaultIfEmpty(ResponseEntity.status(401)
                .build());
    }

    @Override
    public Mono<ResponseEntity<Void>> say(final UUID appIdentifier, final String gameId, final Mono<PlayerSay> playerSay, final ServerWebExchange exchange) {

        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} deleteGame()  userId={} gameId={}", exchange.getRequest()
                .getRemoteAddress(), userId, gameId))
            .flatMap(userId -> playerSay.map(say -> {
                        log.info("{} say() user {} says {}", exchange.getRequest()
                            .getRemoteAddress(), userId, say.getSay());
                        return say.getSay();
                    })
                    .flatMap(say -> gameService.say(appIdentifier, userId, gameId, say))
            )
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok()
                .build()))
            .defaultIfEmpty(ResponseEntity.notFound()
                .build());
    }

    @Override
    public Mono<ResponseEntity<Void>> openLastTrick(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} openLastTrick()  userId={} gameId={}", exchange.getRequest()
                .getRemoteAddress(), userId, gameId))
            .flatMap(userId -> gameService.openLastTrick(userId, gameId)
            )
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok()
                .build()))
            .defaultIfEmpty(ResponseEntity.notFound()
                .build());
    }

    @Override
    public Mono<ResponseEntity<Void>> reload(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} reload()  userId={} gameId={}", exchange.getRequest()
                .getRemoteAddress(), userId, gameId))
            .flatMap(userId -> gameService.reload(appIdentifier, userId, gameId))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok()
                .build()))
            .defaultIfEmpty(ResponseEntity.notFound()
                .build());
    }
}
