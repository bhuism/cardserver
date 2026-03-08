package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converters.service.GameToOpenApiConverter;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import nl.appsource.cardserver.openapi.service.RedisPubSubService;
import nl.appsource.cardserver.service.GameEventType;
import nl.appsource.cardserver.service.GameService;
import nl.appsource.cardserver.service.event.ScheduledGameEvent;
import nl.appsource.generated.openapi.model.Card;
import nl.appsource.generated.openapi.model.ClaimRoemEvent;
import nl.appsource.generated.openapi.model.CloseLastTrickEvent;
import nl.appsource.generated.openapi.model.CreateGame;
import nl.appsource.generated.openapi.model.Game;
import nl.appsource.generated.openapi.model.GetGames200Response;
import nl.appsource.generated.openapi.model.MakeEvent;
import nl.appsource.generated.openapi.model.OpenLastTrickEvent;
import nl.appsource.generated.openapi.model.PassEvent;
import nl.appsource.generated.openapi.model.PlayCardEvent;
import nl.appsource.generated.openapi.model.PlayerSay;
import org.openapitools.api.GamesApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static nl.appsource.cardserver.converters.service.GameToOpenApiConverter.convertCard;

@Slf4j
@RestController
@RequiredArgsConstructor
public class GameController extends AbstractBaseController implements GamesApi, V1Api {

    private final GameService gameService;
    private final GameToOpenApiConverter gameToOpenApiConverter;
    private final UserRepository userRepository;
    private final RedisPubSubService redisPubSubService;

    @Override
    public Mono<ResponseEntity<Game>> getGame(final String gameId, final ServerWebExchange exchange) {
        log.info("{} getGame() gameId={}", exchange.getRequest().getRemoteAddress(), gameId);
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
    public Mono<ResponseEntity<Void>> playCard(final String gameId, final Mono<Card> cardMono, final ServerWebExchange exchange) {
        log.info("{} playCard() gameId={}", exchange.getRequest().getRemoteAddress(), gameId);
        return getUserId(exchange)
            .flatMap(userId -> cardMono
                .flatMap(card -> redisPubSubService.publish("gameEvent", MyServerSentEvent.gameEvent(PlayCardEvent.builder().card(card).build()))
                .doOnNext(_ -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, userId, GameEventType.HUMAN_PLAY_CARD, gameId).setCard(convertCard(card))))))
                .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
                .defaultIfEmpty(ResponseEntity.notFound().build())
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> kickAi(final String gameId, final ServerWebExchange exchange) {
        log.info("{} kickAi() gameId={}", exchange.getRequest().getRemoteAddress(), gameId);
        return getUserId(exchange)
            .doOnNext(userId -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, userId, GameEventType.AI_PLAY_CARD, gameId)))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    public Mono<ResponseEntity<GetGames200Response>> getGames(final Optional<Boolean> boom, final Optional<Boolean> finished, final Optional<Integer> limit, final ServerWebExchange exchange) {
        log.info("{} getGames() boom={} finished={} limit={}", exchange.getRequest().getRemoteAddress(), boom, finished, limit);
        return getUserId(exchange)
            .flatMap(userId -> gameService.getGames(userId, boom.orElse(true), finished.orElse(true), limit.orElse(10))
                .collectList()
                .map(games -> GetGames200Response.builder().games(games).build())
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
            )
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Game>> createGame(final Mono<CreateGame> createGameMono, final ServerWebExchange exchange) {
        log.info("{} createGame()", exchange.getRequest().getRemoteAddress());
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
        log.info("{} deleteGame() gameId={}", exchange.getRequest().getRemoteAddress(), gameId);
        return getUserId(exchange)
            .flatMap(userId -> gameService.deleteGame(userId, gameId)
                .map(_unused -> ResponseEntity.ok().<Void>build())
                .defaultIfEmpty(ResponseEntity.notFound().<Void>build())
            )
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> say(final String gameId, final Mono<PlayerSay> playerSay, final ServerWebExchange exchange) {
        log.info("{} deleteGame() gameId={}", exchange.getRequest().getRemoteAddress(), gameId);
        return getUserId(exchange)
            .flatMap(userId -> playerSay.map(say -> {
                        log.info("{} say() user {} says {}", exchange.getRequest()
                            .getRemoteAddress(), userId, say.getSay());
                        return say.getSay();
                    })
                    .doOnNext(say -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, userId, GameEventType.HUMAN_SAY, gameId).setSay(say)))
                    .flatMap(say -> redisPubSubService.publish("gameEvent", MyServerSentEvent.gameEvent(say ? MakeEvent.builder().build() : PassEvent.builder().build())))
                    .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
                    .defaultIfEmpty(ResponseEntity.notFound().build())
            )
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> openLastTrick(final String gameId, final ServerWebExchange exchange) {
        log.info("{} openLastTrick() gameId={}", exchange.getRequest().getRemoteAddress(), gameId);
        return getUserId(exchange)
            .flatMap(userId -> redisPubSubService.publish("gameEvent", MyServerSentEvent.gameEvent(OpenLastTrickEvent.builder().build()))
            .doOnNext(_ -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, userId, GameEventType.OPEN_LAST_TRICK, gameId))))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> closeLastTrick(final String gameId, final ServerWebExchange exchange) {
        log.info("{} closeLastTrick() gameId={}", exchange.getRequest().getRemoteAddress(), gameId);
        return getUserId(exchange)
            .flatMap(userId -> redisPubSubService.publish("gameEvent", MyServerSentEvent.gameEvent(CloseLastTrickEvent.builder().build()))
            .doOnNext(_ -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, userId, GameEventType.CLOSE_LAST_TRICK, gameId))))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> claimRoem(final String gameId, final ServerWebExchange exchange) {
        log.info("{} claimRoem() gameId={}", exchange.getRequest().getRemoteAddress(), gameId);
        return getUserId(exchange)
            .flatMap(userId -> redisPubSubService.publish("gameEvent", MyServerSentEvent.gameEvent(ClaimRoemEvent.builder().build()))
            .doOnNext(_ -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, userId, GameEventType.CLAIM_ROEM, gameId))))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> claimVerzaken(final String gameId, final ServerWebExchange exchange) {
        log.info("{} claimVerzaken() gameId={}", exchange.getRequest().getRemoteAddress(), gameId);
        return getUserId(exchange)
            .flatMap(userId -> gameService.claimVerzaken(userId, gameId)
                .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.<Void>ok().build()))
                )
            .defaultIfEmpty(ResponseEntity.<Void>status(HttpStatus.UNAUTHORIZED).build());
    }

}
