package nl.appsource.cardserver.controller;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.BoomToOpenApiConverter;
import nl.appsource.cardserver.converter.GameToOpenApiConverter;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.BoomRepository;
import nl.appsource.cardserver.repository.GameRepository;
import nl.appsource.cardserver.repository.SseSessionRepository;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.BoomService;
import nl.appsource.cardserver.service.GameEngineImpl;
import nl.appsource.cardserver.service.GameService;
import nl.appsource.cardserver.utils.CardServerAuthentication;
import org.openapitools.api.BoomApi;
import org.openapitools.model.Boom;
import org.openapitools.model.CreateBoom;
import org.openapitools.model.Game;
import org.openapitools.model.GetBooms200Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@RestController
@Slf4j
public class BoomController extends GenericController implements BoomApi, V1Api {

    private final BoomService boomService;

    private final BoomToOpenApiConverter boomToOpenApiConverter;

    private final BoomRepository boomRepository;

    private final GameRepository gameRepository;

    private final GameService gameService;

    private final GameToOpenApiConverter gameToOpenApiConverter;

    private final ConcurrentMap<String, Object> lockMap = new ConcurrentHashMap<>();

    private static final Random RAND = new SecureRandom();

    public BoomController(final SseSessionRepository sseSessionRepositoryArg, final BoomService boolServiceArg, final BoomToOpenApiConverter boomToOpenApiConverterArg, final GameRepository gameRepositoryArg, final BoomRepository boomRepositoryArg, final GameService gameServiceArg, final GameToOpenApiConverter gameToOpenApiConverterArg, final UserRepository userRepositoryArg) {
        super(userRepositoryArg, sseSessionRepositoryArg);
        this.boomService = boolServiceArg;
        this.boomToOpenApiConverter = boomToOpenApiConverterArg;
        this.gameRepository = gameRepositoryArg;
        this.boomRepository = boomRepositoryArg;
        this.gameService = gameServiceArg;
        this.gameToOpenApiConverter = gameToOpenApiConverterArg;
    }

    @Override
    public Mono<ResponseEntity<Boom>> createBoom(final Mono<CreateBoom> createBoomMono, final Optional<String> appIdentifier, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((auth) -> log.info("{} createBoom() userId={}", exchange.getRequest().getRemoteAddress(), auth.user().getId()))
            .flatMap(auth -> createBoomMono.flatMap(createBoom -> boomService.createBoom(auth.user().getId(), createBoom.getPlayers(), auth.user().getGameVariant(), auth.user().getAiRisc())))
            .flatMap(boomToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<Boom>> getBoom(final String boomId, final Optional<String> appIdentifier, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((auth) -> log.info("{} getBoom() userId={} boomId={}", exchange.getRequest()
                .getRemoteAddress(), auth.user().getId(), boomId))
            .flatMap(auth -> boomService.getBoom(auth.user().getId(), boomId))
            .flatMap(boomToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("{} getBoom({}), boom not found", exchange.getRequest()
                    .getRemoteAddress(), boomId);
                return Mono.empty();
            }))
            .defaultIfEmpty(ResponseEntity.notFound()
                .build());
    }


    @Override
    public Mono<ResponseEntity<GetBooms200Response>> getBooms(final Optional<String> appIdentifier, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((auth) -> log.info("{} getBooms() userId={}", exchange.getRequest()
                .getRemoteAddress(), auth.user().getId()))
            .flatMap(auth -> boomService.getBooms(auth.user().getId())
                .collectList()
                .map(booms -> new GetBooms200Response().booms(booms))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
            )
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());

    }

    private Mono<Integer> calcDealer(final nl.appsource.cardserver.model.Boom boom) {

        if (boom.getGames().isEmpty()) {
            return Mono.just(RAND.nextInt(4));
        }

        return boomRepository.findById(boom.getGames().getLast())
            .map(game -> (game.getDealer() + 1) % 4);

    }


    @Override
    public Mono<ResponseEntity<Game>> playBoom(final String boomId, final Optional<String> appIdentifier, final ServerWebExchange exchange) {
        synchronized (lockMap.computeIfAbsent(boomId, _unused -> new Object())) {
            return authorize(appIdentifier, exchange)
                .doOnNext((auth) -> log.info("{} playBoom() userId={} boomId={}", exchange.getRequest().getRemoteAddress(), auth.user().getId(), boomId))
                .flatMap(auth -> {
                    return boomRepository.findById(boomId)
                        .map((boom) -> {
                            return Flux.fromIterable(boom.getGames())
                                .flatMap(gameRepository::findById)
                                .filter(game -> !new GameEngineImpl(game).isCompleted())
                                .next()
                                .switchIfEmpty(Mono.defer(() -> {
                                    if (boom.getGames().size() < 16) {
                                        return calcDealer(boom).flatMap(dealer -> {
                                            return gameService.createGame(auth.user().getId(), boom.getPlayers(), boom.getGameVariant(), boom.getId(), dealer, boom.getAiRisc())
                                                .doOnNext(game -> boom.getGames().add(game.getId()))
                                                .flatMap(game -> boomRepository.save(boom).thenReturn(game));
                                        });
                                    } else {
                                        return Mono.empty();
                                    }
                                }))
                                .mapNotNull(gameToOpenApiConverter::convert)
                                .map(ResponseEntity::ok)
                                .defaultIfEmpty(ResponseEntity.notFound()
                                    .build());
                        });
                })
                .flatMap(responseEntityMono -> responseEntityMono);
        }
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteBoom(final String boomId, final Optional<String> appIdentifier, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((auth) -> log.info("{} deleteBoom() userId={} boomId={}", exchange.getRequest().getRemoteAddress(), auth.user().getId(), boomId))
            .map(CardServerAuthentication::user)
            .map(User::getId)
            .flatMap(boomRepository::deleteById)
            .then(Mono.just(ResponseEntity.ok().build()));
    }


}
