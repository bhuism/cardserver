package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.GameToOpenApiConverter;
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

    @Override
    public Mono<ResponseEntity<Game>> getGame(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        log.info("{} getGame({})", exchange.getRequest().getRemoteAddress(), gameId);
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .filter((userId) -> sseEmitterRepository.validate(appIdentifier, userId))
            .flatMap(userId -> gameService.getGame(userId, gameId))
            .mapNotNull(gameToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @Override
    public Mono<ResponseEntity<PlayCardResponse>> playCard(final UUID appIdentifier, final String gameId, final Mono<PlayCard> playCardMono, final ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .flatMap(userId -> playCardMono.map(playCard -> {
                        log.info("{} playCard() userI={} plays card={}", exchange.getRequest().getRemoteAddress(), userId, playCard.getCard());
                        return playCard;
                    })
                    .flatMap(playCard -> gameService.playCard(appIdentifier, userId, gameId, convertCard(playCard.getCard())))
//                    .onErrorResume(GameEngineException.class, throwable -> {
//                        sseEmitterRepository.sendUserMessage();
//                        gameService.sendUserMessage(new UserMessage().userId(userId).message(throwable.getMessage()).variant(UserMessage.VariantEnum.ERROR));
//                        return just(new PlayCardResponse().cardWasPlayed(false));
//                    })
//                    .onErrorResume(Throwable.class, throwable -> {
//                        log.error("", throwable);
//                        gameService.sendUserMessage(new UserMessage().userId(userId).message(throwable.getClass().getName() + ":" + throwable.getMessage()).variant(UserMessage.VariantEnum.ERROR));
//                        return just(new PlayCardResponse().cardWasPlayed(false));
//                    })
            )

            .map(ResponseEntity::ok)
            .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @Override
    public Mono<ResponseEntity<Void>> kickAi(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .doOnNext(userId -> gameService.finishWithAi(gameId, Duration.ZERO))
            .then(just(ResponseEntity.ok().build()));
    }

    @Override
    public Mono<ResponseEntity<Flux<Game>>> getGames(final UUID appIdentifier, final ServerWebExchange exchange) {
//        log.info("{} getGames()", exchange.getRequest().getRemoteAddress());
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .mapNotNull(userId -> gameService.getGames(userId).mapNotNull(gameToOpenApiConverter::convert))
            .map(ResponseEntity::ok)
            .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));

    }

    @Override
    public Mono<ResponseEntity<Game>> createGame(final UUID appIdentifier, final Mono<CreateGame> createGameMono, final ServerWebExchange exchange) {
        log.info("{} createGame()", exchange.getRequest().getRemoteAddress());
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .flatMap(userId -> createGameMono.flatMap(createGame -> gameService.createGame(userId, createGame.getPlayers())))
            .mapNotNull(gameToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));

    }

    @Override
    public Mono<ResponseEntity<Void>> deleteGame(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        log.info("{} deleteGame({})", gameId, exchange.getRequest().getRemoteAddress());
        return gameService.deleteGame(gameId)
            .map(ResponseEntity::ok)
            .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @Override
    public Mono<ResponseEntity<Void>> say(final UUID appIdentifier, final String gameId, final Mono<PlayerSay> playerSay, final ServerWebExchange exchange) {

        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .flatMap(userId -> playerSay.map(say -> {
                        log.info("{} say() user {} says {}", exchange.getRequest().getRemoteAddress(), userId, say.getSay());
                        return say.getSay();
                    })
                    .flatMap(say -> gameService.say(appIdentifier, userId, gameId, say))
//                .onErrorResume(GameEngineException.class, throwable -> {
//                    gameService.sendUserMessage(new UserMessage().userId(userId).message(throwable.getMessage()).variant(UserMessage.VariantEnum.ERROR));
//                    return Mono.empty();
//                })
//                .onErrorResume(Throwable.class, throwable -> {
//                    log.error("", throwable);
//                    gameService.sendUserMessage(new UserMessage().userId(userId).message(throwable.getClass().getName() + ":" + throwable.getMessage()).variant(UserMessage.VariantEnum.ERROR));
//                    return Mono.empty();
//                })
            )
            .map(ResponseEntity::ok)
            .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @Override
    public Mono<ResponseEntity<Void>> openLastTrick(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .flatMap(userId -> gameService.openLastTrick(userId, gameId)
//                .onErrorResume(GameEngineException.class, throwable -> {
//                    gameService.sendUserMessage(new UserMessage().userId(userId).message(throwable.getMessage()).variant(UserMessage.VariantEnum.ERROR));
//                    return Mono.empty();
//                })
//                .onErrorResume(Throwable.class, throwable -> {
//                    log.error("", throwable);
//                    gameService.sendUserMessage(new UserMessage().userId(userId).message(throwable.getClass().getName() + ":" + throwable.getMessage()).variant(UserMessage.VariantEnum.ERROR));
//                    return Mono.empty();
//                })
            )
            .map(ResponseEntity::ok)
            .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }
}

