package nl.appsource.cardsever.api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converters.service.GameToOpenApiConverter;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.openapi.service.RedisStreamService;
import nl.appsource.cardsever.api.service.GameService;
import nl.appsource.generated.openapi.model.CreateGame;
import nl.appsource.generated.openapi.model.Game;
import nl.appsource.generated.openapi.model.GameEvent;
import nl.appsource.generated.openapi.model.GetGames200Response;
import org.openapitools.api.GamesApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
public class GameController extends AbstractBaseController implements GamesApi, V1Api {

    private final GameService gameService;
    private final GameToOpenApiConverter gameToOpenApiConverter;
    private final UserRepository userRepository;
    private final RedisStreamService redisStreamService;

    @Override
    public Mono<ResponseEntity<Game>> getGame(final String gameId, final ServerWebExchange exchange) {
//        log.info("{} getGame() gameId={}", exchange.getRequest().getRemoteAddress(), gameId);
        return getUserId(exchange)
            .flatMap(userId -> gameService.getGame(userId, gameId)
                .mapNotNull(gameToOpenApiConverter::convert)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("{} getGame({}), game not found", exchange.getRequest()
                        .getRemoteAddress(), gameId);
                    return Mono.empty();
                }))
                .defaultIfEmpty(ResponseEntity.notFound().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<GetGames200Response>> getGames(final Boolean boom, final Boolean finished, final Integer limit, final ServerWebExchange exchange) {
//        log.info("{} getGames() boom={} finished={} limit={}", exchange.getRequest().getRemoteAddress(), boom, finished, limit);
        return getUserId(exchange)
            .flatMap(userId -> gameService.getGames(userId, boom != null && boom, finished != null && finished, limit == null ? 10 : limit)
                .collectList()
                .map(games -> new GetGames200Response().games(games))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
            )
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Game>> createGame(final Mono<CreateGame> createGameMono, final ServerWebExchange exchange) {
//        log.info("{} createGame()", exchange.getRequest().getRemoteAddress());
        return getUserId(exchange)
            .flatMap(userId -> userRepository.findById(userId).flatMap(user -> createGameMono.flatMap(createGame -> gameService.createGame(user.getId(), createGame.getPlayers(), user.getGameVariant(), user.getAiRisc())))
                .mapNotNull(gameToOpenApiConverter::convert)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
            )
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteGame(final String gameId, final ServerWebExchange exchange) {
//        log.info("{} deleteGame() gameId={}", exchange.getRequest().getRemoteAddress(), gameId);
        return getUserId(exchange)
            .flatMap(userId -> gameService.deleteGame(userId, gameId)
                .map(_unused -> ResponseEntity.ok().<Void>build())
                .defaultIfEmpty(ResponseEntity.notFound().<Void>build())
            )
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> gameEvent(final String gameId, final Mono<GameEvent> gameEventMono, final ServerWebExchange exchange) {
        return getUserId(exchange)
            .flatMap(userId -> gameEventMono
                .doOnNext(gameEvent -> log.info("{} gameEvent() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), userId, gameEvent.getGameId()))
                .flatMap(gameEvent -> {
                        gameEvent.setGameId(gameId);
                        gameEvent.setUserId(userId);
                        return redisStreamService.publishToStream("gameEvent", gameEvent)
                            .switchIfEmpty(Mono.defer(() -> {
                                    log.warn("publishToStream returned null");
                                    return Mono.empty();
                                })
                            );
                    }
                )
                .thenReturn(ResponseEntity.ok().<Void>build())
            )
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

}
