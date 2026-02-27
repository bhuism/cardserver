package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converters.GameToOpenApiConverter;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.service.GameEventType;
import nl.appsource.cardserver.service.GameService;
import nl.appsource.cardserver.service.event.ScheduledGameEvent;
import nl.appsource.generated.openapi.model.CreateGame;
import nl.appsource.generated.openapi.model.Game;
import nl.appsource.generated.openapi.model.GetGames200Response;
import nl.appsource.generated.openapi.model.PlayCard;
import nl.appsource.generated.openapi.model.PlayerSay;
import org.openapitools.api.GamesApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
public class GameController extends AbstractBaseController implements GamesApi, V1Api {

    private final GameService gameService;
    private final GameToOpenApiConverter gameToOpenApiConverter;
    private final UserRepository userRepository;

    @Override
    public Mono<ResponseEntity<Game>> getGame(final String appIdentifier, final String gameId, final ServerWebExchange exchange) {
        log.info("{} getGame() appIdentifier={} gameId={}", exchange.getRequest().getRemoteAddress(), appIdentifier, gameId);
        return authorize(appIdentifier, exchange)
            .flatMap(auth -> gameService.getGame(auth.userId(), gameId)
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
    public Mono<ResponseEntity<Void>> playCard(final String appIdentifier, final String gameId, final Mono<PlayCard> playCardMono, final ServerWebExchange exchange) {
        log.info("{} playCard() appIdentifier={} gameId={}", exchange.getRequest().getRemoteAddress(), appIdentifier, gameId);
        return authorize(appIdentifier, exchange)
            .flatMap(auth -> playCardMono.map(PlayCard::getCard)
                .map(GameToOpenApiConverter::convertCard)
                .doOnNext(playCard -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, auth.userId(), GameEventType.HUMAN_PLAY_CARD, gameId).setCard(playCard)))
                .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
                .defaultIfEmpty(ResponseEntity.notFound().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> kickAi(final String appIdentifier, final String gameId, final ServerWebExchange exchange) {
        log.info("{} kickAi() appIdentifier={} gameId={}", exchange.getRequest().getRemoteAddress(), appIdentifier, gameId);
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, auth.userId(), GameEventType.AI_PLAY_CARD, gameId)))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    public Mono<ResponseEntity<GetGames200Response>> getGames(final String appIdentifier, final Optional<Boolean> boom, final Optional<Boolean> finished, final Optional<Integer> limit, final ServerWebExchange exchange) {
        log.info("{} getGames() appIdentifier={} boom={} finished={} limit={}", exchange.getRequest().getRemoteAddress(), appIdentifier, boom, finished, limit);
        return authorize(appIdentifier, exchange)
            .flatMap(auth -> gameService.getGames(auth.userId(), boom.orElse(true), finished.orElse(true), limit.orElse(10))
                .collectList()
                .map(games -> GetGames200Response.builder().games(games).build())
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
            )
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Game>> createGame(final String appIdentifier, final Mono<CreateGame> createGameMono, final ServerWebExchange exchange) {
        log.info("{} createGame() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
            .flatMap(auth -> userRepository.findById(auth.userId()).flatMap(user -> createGameMono.flatMap(createGame -> gameService.createGame(user.getId(), createGame.getPlayers(), user.getGameVariant(), user.getAiRisc())))
                .mapNotNull(gameToOpenApiConverter::convert)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
            )
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteGame(final String appIdentifier, final String gameId, final ServerWebExchange exchange) {
        log.info("{} deleteGame() appIdentifier={} gameId={}", exchange.getRequest().getRemoteAddress(), appIdentifier, gameId);
        return authorize(appIdentifier, exchange)
            .flatMap(auth -> gameService.deleteGame(auth.userId(), gameId)
                .map(_unused -> ResponseEntity.ok().<Void>build())
                .defaultIfEmpty(ResponseEntity.notFound().<Void>build())
            )
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> say(final String appIdentifier, final String gameId, final Mono<PlayerSay> playerSay, final ServerWebExchange exchange) {
        log.info("{} deleteGame() appIdentifier={} gameId={}", exchange.getRequest().getRemoteAddress(), appIdentifier, gameId);
        return authorize(appIdentifier, exchange)
            .flatMap(auth -> playerSay.map(say -> {
                        log.info("{} say() user {} says {}", exchange.getRequest()
                            .getRemoteAddress(), auth.userId(), say.getSay());
                        return say.getSay();
                    })
                    .doOnNext(say -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, auth.userId(), GameEventType.HUMAN_SAY, gameId).setSay(say)))
                    .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
                    .defaultIfEmpty(ResponseEntity.notFound().build())
            )
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> openLastTrick(final String appIdentifier, final String gameId, final ServerWebExchange exchange) {
        log.info("{} openLastTrick() appIdentifier={} gameId={}", exchange.getRequest().getRemoteAddress(), appIdentifier, gameId);
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, auth.userId(), GameEventType.OPEN_LAST_TRICK, gameId)))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> closeLastTrick(final String appIdentifier, final String gameId, final ServerWebExchange exchange) {
        log.info("{} closeLastTrick() appIdentifier={} gameId={}", exchange.getRequest().getRemoteAddress(), appIdentifier, gameId);
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, auth.userId(), GameEventType.CLOSE_LAST_TRICK, gameId)))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> claimRoem(final String appIdentifier, final String gameId, final ServerWebExchange exchange) {
        log.info("{} claimRoem() appIdentifier={} gameId={}", exchange.getRequest().getRemoteAddress(), appIdentifier, gameId);
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> gameService.scheduleGameEvent(new ScheduledGameEvent(0, auth.userId(), GameEventType.CLAIM_ROEM, gameId)))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> claimVerzaken(final String appIdentifier, final String gameId, final ServerWebExchange exchange) {
        log.info("{} claimVerzaken() appIdentifier={} gameId={}", exchange.getRequest().getRemoteAddress(), appIdentifier, gameId);
        return authorize(appIdentifier, exchange)
            .flatMap(auth -> gameService.claimVerzaken(auth, gameId).then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build())))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

}
