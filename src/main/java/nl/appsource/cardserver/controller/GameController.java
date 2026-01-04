package nl.appsource.cardserver.controller;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.GameToOpenApiConverter;
import nl.appsource.cardserver.repository.SseSessionRepository;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.GameEventType;
import nl.appsource.cardserver.service.GameService;
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

@Slf4j
@RestController
public class GameController extends GenericController implements GamesApi, V1Api {

    private final GameService gameService;

    private final GameToOpenApiConverter gameToOpenApiConverter;

    public GameController(final GameService gameService, final GameToOpenApiConverter gameToOpenApiConverter, final UserRepository userRepository, final SseSessionRepository sseSessionRepository) {
        super(userRepository, sseSessionRepository);
        this.gameService = gameService;
        this.gameToOpenApiConverter = gameToOpenApiConverter;
    }

    @Override
    public Mono<ResponseEntity<Game>> getGame(final String appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> log.info("{} getGame() userId={} gameId={}", exchange.getRequest()
                .getRemoteAddress(), auth.user().getId(), gameId))
            .flatMap(auth -> gameService.getGame(auth.user().getId(), gameId)
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
    public Mono<ResponseEntity<Void>> playCard(final String appIdentifier, final String gameId, final Mono<PlayCard> playCardMono, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> log.info("{} playCard() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), auth.user().getId(), gameId))
            .flatMap(auth -> playCardMono.map(PlayCard::getCard)
                .map(GameToOpenApiConverter::convertCard)
                .doOnNext(playCard -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, auth.user().getId(), GameEventType.HUMAN_PLAY_CARD, gameId).setCard(playCard)))
                .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
                .defaultIfEmpty(ResponseEntity.notFound().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> kickAi(final String appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> log.info("{} kickAi() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), auth.user().getId(), gameId))
            .doOnNext(auth -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, auth.user().getId(), GameEventType.AI_PLAY_CARD, gameId)))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }


    @Override
    public Mono<ResponseEntity<GetGames200Response>> getGames(final String appIdentifier, final Optional<Boolean> boom, final Optional<Boolean> finished, final Optional<Integer> limit, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> log.info("{} getGames() userId={} boom={} finished={}", exchange.getRequest().getRemoteAddress(), auth.user().getId(), boom, finished))
            .flatMap(auth -> gameService.getGames(auth.user().getId(), boom.orElse(true), finished.orElse(true), limit.orElse(10))
                .collectList()
                .map(games -> new GetGames200Response().games(games))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
            )
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Game>> createGame(final String appIdentifier, final Mono<CreateGame> createGameMono, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> log.info("{} createGame() userId={}", exchange.getRequest().getRemoteAddress(), auth.user().getId()))
            .flatMap(auth -> createGameMono.flatMap(createGame -> gameService.createGame(auth.user().getId(), createGame.getPlayers(), auth.user().getGameVariant())))
                .mapNotNull(gameToOpenApiConverter::convert)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound()
                    .build())
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteGame(final String appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> log.info("{} deleteGame() userId={} gameId={}", exchange.getRequest()
                .getRemoteAddress(), auth.user().getId(), gameId))
            .flatMap(auth -> gameService.deleteGame(auth.user().getId(), gameId)
                .defaultIfEmpty(false)
                .map(deleted -> deleted
                    ? ResponseEntity.noContent()
                    .<Void>build()
                    : ResponseEntity.notFound()
                    .<Void>build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> say(final String appIdentifier, final String gameId, final Mono<PlayerSay> playerSay, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> log.info("{} deleteGame() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), auth.user().getId(), gameId))
            .flatMap(auth -> playerSay.map(say -> {
                        log.info("{} say() user {} says {}", exchange.getRequest()
                            .getRemoteAddress(), auth.user().getId(), say.getSay());
                        return say.getSay();
                    })
                    .doOnNext(say -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, auth.user().getId(), GameEventType.HUMAN_SAY, gameId).setSay(say)))
                    .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
                    .defaultIfEmpty(ResponseEntity.<Void>notFound().build())
            )
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> openLastTrick(final String appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> log.info("{} openLastTrick() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), auth.user().getId(), gameId))
            .doOnNext(auth -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, auth.user().getId(), GameEventType.OPEN_LAST_TRICK, gameId)))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> closeLastTrick(final String appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> log.info("{} closeLastTrick() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), auth.user().getId(), gameId))
            .doOnNext(auth -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, auth.user().getId(), GameEventType.CLOSE_LAST_TRICK, gameId)))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }


    @Override
    public Mono<ResponseEntity<Void>> reloadGame(final String appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> log.info("{} reloadGame() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), auth.user().getId(), gameId))
            .flatMap(auth -> gameService.reload(appIdentifier, auth.user().getId(), gameId))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> claimRoem(final String appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> log.info("{} claimRoem() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), auth.user().getId(), gameId))
            .flatMap(auth -> gameService.claimRoem(appIdentifier, auth.user().getId(), gameId))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> claimVerzaken(final String appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> log.info("{} claimVerzaken() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), auth.user().getId(), gameId))
            .flatMap(auth -> gameService.claimVerzaken(appIdentifier, auth.user().getId(), gameId).then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build())))
            .defaultIfEmpty(ResponseEntity.<Void>status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> gameMessage(final String appIdentifier, final String gameId, final Mono<PostMessage> arg, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> log.info("{} gameMessage() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), auth.user().getId(), gameId))
            .flatMap(auth -> arg.flatMap((message -> gameService.gameMessage(auth.user().getId(), gameId, message.getMessage())))
                .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
                .defaultIfEmpty(ResponseEntity.<Void>notFound().build()))
            .defaultIfEmpty(ResponseEntity.<Void>status(HttpStatus.UNAUTHORIZED).build());
    }

}
