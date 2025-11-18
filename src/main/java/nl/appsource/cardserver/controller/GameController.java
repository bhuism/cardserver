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
import org.openapitools.model.PlayCard;
import org.openapitools.model.PlayerSay;
import org.openapitools.model.PostMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static reactor.core.publisher.Mono.just;

@Slf4j
@RestController
public class GameController extends GenericController implements GamesApi {

    private final GameService gameService;

    private final GameToOpenApiConverter gameToOpenApiConverter;

    private final UserRepository userRepository;

    public GameController(final SseEmitterRepository sseEmitterRepository, final GameService gameServiceArg, final GameToOpenApiConverter gameToOpenApiConverterArg, final UserRepository userRepositoryArg) {
        super(sseEmitterRepository);
        this.gameService = gameServiceArg;
        this.gameToOpenApiConverter = gameToOpenApiConverterArg;
        this.userRepository = userRepositoryArg;
    }

    @Override
    public Mono<ResponseEntity<Game>> getGame(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} getGame() userId={} gameId={}", exchange.getRequest()
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
    public Mono<ResponseEntity<Void>> playCard(final UUID appIdentifier, final String gameId, final Mono<PlayCard> playCardMono, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} playCard() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), userId, gameId))
            .flatMap(userId -> playCardMono.map(PlayCard::getCard)
                .map(GameToOpenApiConverter::convertCard)
                .doOnNext(playCard -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, userId, GameEventType.HUMAN_PLAY_CARD, gameId).setCard(playCard)))
            )
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<Void>> kickAi(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} kickAi() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), userId, gameId))
            .doOnNext(userId -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, userId, GameEventType.AI_PLAY_CARD, gameId)))
            .then(just(ResponseEntity.ok().build()));
    }

    @Override
    public Mono<ResponseEntity<Flux<String>>> getGames(final UUID appIdentifier, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} getGames() userId={}", exchange.getRequest()
                .getRemoteAddress(), userId))
            .mapNotNull(userId -> gameService.getGames(userId))
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound()
                .build());
    }

    @Override
    public Mono<ResponseEntity<Game>> createGame(final UUID appIdentifier, final Mono<CreateGame> createGameMono, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} createGame() userId={}", exchange.getRequest().getRemoteAddress(), userId))
            .flatMap(userRepository::findById)
            .flatMap(user -> createGameMono.flatMap(createGame -> gameService.createGame(user.getId(), createGame.getPlayers(), user.getGameVariant())))
            .mapNotNull(gameToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound()
                .build());
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteGame(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} deleteGame() userId={} gameId={}", exchange.getRequest()
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
            .doOnNext((userId) -> log.info("{} deleteGame() userId={} gameId={}", exchange.getRequest()
                .getRemoteAddress(), userId, gameId))
            .flatMap(userId -> playerSay.map(say -> {
                        log.info("{} say() user {} says {}", exchange.getRequest()
                            .getRemoteAddress(), userId, say.getSay());
                        return say.getSay();
                    })
                    .doOnNext(say -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, userId, GameEventType.HUMAN_SAY, gameId).setSay(say)))
            )
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<Void>> openLastTrick(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} openLastTrick() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), userId, gameId))
            .doOnNext(userId -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, userId, GameEventType.OPEN_LAST_TRICK, gameId)))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok()
                .build()))
            .defaultIfEmpty(ResponseEntity.notFound()
                .build());
    }

    @Override
    public Mono<ResponseEntity<Void>> closeLastTrick(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} closeLastTrick() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), userId, gameId))
            .doOnNext(userId -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, userId, GameEventType.CLOSE_LAST_TRICK, gameId)))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok()
                .build()))
            .defaultIfEmpty(ResponseEntity.notFound()
                .build());
    }


    @Override
    public Mono<ResponseEntity<Void>> reload(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} reload() userId={} gameId={}", exchange.getRequest()
                .getRemoteAddress(), userId, gameId))
            .flatMap(userId -> gameService.reload(appIdentifier, userId, gameId))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok()
                .build()))
            .defaultIfEmpty(ResponseEntity.notFound()
                .build());
    }


    @Override
    public Mono<ResponseEntity<Void>> claimRoem(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} claimRoem() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), userId, gameId))
            .flatMap(userId -> gameService.claimRoem(appIdentifier, userId, gameId))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok()
                .build()))
            .defaultIfEmpty(ResponseEntity.notFound()
                .build());
    }

    @Override
    public Mono<ResponseEntity<Void>> claimVerzaken(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} claimRoem() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), userId, gameId))
            .flatMap(userId -> gameService.claimVerzaken(appIdentifier, userId, gameId))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok()
                .build()))
            .defaultIfEmpty(ResponseEntity.notFound()
                .build());
    }

    @Override
    public Mono<ResponseEntity<Void>> gameMessage(final UUID appIdentifier, final String gameId, final Mono<PostMessage> arg, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} gameMessage() userId={} gameId={}", exchange.getRequest().getRemoteAddress(), userId, gameId))
            .flatMap(userId -> arg.flatMap((message -> gameService.gameMessage(userId, gameId, message.getMessage()))))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

}
