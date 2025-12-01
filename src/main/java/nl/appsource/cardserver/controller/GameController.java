package nl.appsource.cardserver.controller;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.GameToOpenApiConverter;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.GameEventType;
import nl.appsource.cardserver.service.GameService;
import nl.appsource.cardserver.service.SseEmitterRepository;
import nl.appsource.cardserver.service.event.ScheduledGameEvent;
import org.openapitools.api.GamesApi;
import org.openapitools.model.CreateGame;
import org.openapitools.model.Game;
import org.openapitools.model.GetGames200Response;
import org.openapitools.model.PlayCard;
import org.openapitools.model.PlayerSay;
import org.openapitools.model.PostMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
public class GameController extends GenericController implements GamesApi, V1Api {

    private final GameService gameService;

    private final GameToOpenApiConverter gameToOpenApiConverter;

    public GameController(final SseEmitterRepository sseEmitterRepository, final GameService gameServiceArg, final GameToOpenApiConverter gameToOpenApiConverterArg, final UserRepository userRepositoryArg) {
        super(sseEmitterRepository, userRepositoryArg);
        this.gameService = gameServiceArg;
        this.gameToOpenApiConverter = gameToOpenApiConverterArg;
    }

    @Override
    public Mono<ResponseEntity<Game>> getGame(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} getGame() userId={} gameId={}", exchange.getRequest()
                .getRemoteAddress(), user.getId(), gameId))
            .flatMap(user -> gameService.getGame(user.getId(), gameId)
                .mapNotNull(gameToOpenApiConverter::convert)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("{} getGame({}), game not found", exchange.getRequest()
                        .getRemoteAddress(), gameId);
                    return Mono.empty();
                }))
                .defaultIfEmpty(ResponseEntity.notFound()
                    .build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> playCard(final UUID appIdentifier, final String gameId, final Mono<PlayCard> playCardMono, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} playCard() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), user.getId(), gameId))
            .flatMap(user -> playCardMono.map(PlayCard::getCard)
                .map(GameToOpenApiConverter::convertCard)
                .doOnNext(playCard -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, user.getId(), GameEventType.HUMAN_PLAY_CARD, gameId).setCard(playCard)))
                .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
                .defaultIfEmpty(ResponseEntity.notFound().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> kickAi(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} kickAi() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), user.getId(), gameId))
            .doOnNext(user -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, user.getId(), GameEventType.AI_PLAY_CARD, gameId)))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }


    @Override
    public Mono<ResponseEntity<GetGames200Response>> getGames(final UUID appIdentifier, final Optional<Boolean> boom, final Optional<Boolean> finished, final Optional<Integer> limit, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} getGames() userId={} boom={} finished={}", exchange.getRequest().getRemoteAddress(), user.getId(), boom, finished))
            .flatMap(user -> gameService.getGames(user.getId(), boom.orElse(true), finished.orElse(true), limit.orElse(10))
                .collectList()
                .map(games -> new GetGames200Response().games(games))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
            )
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Game>> createGame(final UUID appIdentifier, final Mono<CreateGame> createGameMono, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} createGame() userId={}", exchange.getRequest().getRemoteAddress(), user.getId()))
                .flatMap(user -> createGameMono.flatMap(createGame -> gameService.createGame(user.getId(), createGame.getPlayers(), user.getGameVariant())))
                .mapNotNull(gameToOpenApiConverter::convert)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound()
                    .build())
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteGame(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} deleteGame() userId={} gameId={}", exchange.getRequest()
                .getRemoteAddress(), user.getId(), gameId))
            .flatMap(user -> gameService.deleteGame(user.getId(), gameId)
                .defaultIfEmpty(false)
                .map(deleted -> deleted
                    ? ResponseEntity.noContent()
                    .<Void>build()
                    : ResponseEntity.notFound()
                    .<Void>build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> say(final UUID appIdentifier, final String gameId, final Mono<PlayerSay> playerSay, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} deleteGame() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), user.getId(), gameId))
            .flatMap(user -> playerSay.map(say -> {
                        log.info("{} say() user {} says {}", exchange.getRequest()
                            .getRemoteAddress(), user.getId(), say.getSay());
                        return say.getSay();
                    })
                    .doOnNext(say -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, user.getId(), GameEventType.HUMAN_SAY, gameId).setSay(say)))
                    .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
                    .defaultIfEmpty(ResponseEntity.notFound().build())
            )
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> openLastTrick(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} openLastTrick() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), user.getId(), gameId))
            .doOnNext(user -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, user.getId(), GameEventType.OPEN_LAST_TRICK, gameId)))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> closeLastTrick(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} closeLastTrick() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), user.getId(), gameId))
            .doOnNext(user -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, user.getId(), GameEventType.CLOSE_LAST_TRICK, gameId)))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }


    @Override
    public Mono<ResponseEntity<Void>> reloadGame(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} reloadGame() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), user.getId(), gameId))
            .flatMap(user -> gameService.reload(appIdentifier, user.getId(), gameId))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> claimRoem(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} claimRoem() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), user.getId(), gameId))
            .flatMap(user -> gameService.claimRoem(appIdentifier, user.getId(), gameId))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> claimVerzaken(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} claimVerzaken() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), user.getId(), gameId))
            .flatMap(user -> gameService.claimVerzaken(appIdentifier, user.getId(), gameId).then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build())))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> gameMessage(final UUID appIdentifier, final String gameId, final Mono<PostMessage> arg, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} gameMessage() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), user.getId(), gameId))
            .flatMap(user -> arg.flatMap((message -> gameService.gameMessage(user.getId(), gameId, message.getMessage())))
                .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
                .defaultIfEmpty(ResponseEntity.notFound().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

}
