package nl.appsource.cardserver.controller;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.BoomToOpenApiConverter;
import nl.appsource.cardserver.converter.GameToOpenApiConverter;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.BoomRepository;
import nl.appsource.cardserver.repository.GameRepository;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.BoomService;
import nl.appsource.cardserver.service.GameEngineImpl;
import nl.appsource.cardserver.service.GameService;
import nl.appsource.cardserver.service.SseEmitterRepository;
import org.openapitools.api.BoomApi;
import org.openapitools.model.Boom;
import org.openapitools.model.CreateBoom;
import org.openapitools.model.Game;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;
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

    public BoomController(final SseEmitterRepository sseEmitterRepository, final BoomService boolServiceArg, final BoomToOpenApiConverter boomToOpenApiConverterArg, final GameRepository gameRepositoryArg, final BoomRepository boomRepositoryArg, final GameService gameServiceArg, final GameToOpenApiConverter gameToOpenApiConverterArg, final UserRepository userRepositoryArg) {
        super(sseEmitterRepository, userRepositoryArg);
        this.boomService = boolServiceArg;
        this.boomToOpenApiConverter = boomToOpenApiConverterArg;
        this.gameRepository = gameRepositoryArg;
        this.boomRepository = boomRepositoryArg;
        this.gameService = gameServiceArg;
        this.gameToOpenApiConverter = gameToOpenApiConverterArg;
    }

    @Override
    public Mono<ResponseEntity<Boom>> createBoom(final UUID appIdentifier, final Mono<CreateBoom> createBoomMono, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} createBoom() userId={}", exchange.getRequest().getRemoteAddress(), user.getId()))
            .flatMap(this::updateUser)
            .flatMap(user -> createBoomMono.flatMap(createBoom -> boomService.createBoom(user.getId(), createBoom.getPlayers(), user.getGameVariant())))
            .mapNotNull(boomToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound()
                .build());
    }

    @Override
    public Mono<ResponseEntity<Boom>> getBoom(final UUID appIdentifier, final String boomId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} getBoom() userId={} boomId={}", exchange.getRequest()
                .getRemoteAddress(), user.getId(), boomId))
            .flatMap(user -> boomService.getBoom(user.getId(), boomId))
            .mapNotNull(boomToOpenApiConverter::convert)
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
    public Mono<ResponseEntity<Flux<Boom>>> getBooms(final UUID appIdentifier, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} getBooms() userId={}", exchange.getRequest()
                .getRemoteAddress(), user.getId()))
            .mapNotNull(user -> boomService.getBooms(user.getId()).mapNotNull(boomToOpenApiConverter::convert))
            .mapNotNull(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound()
                .build());
    }

    private Mono<Integer> calcDealer(final nl.appsource.cardserver.model.Boom boom) {

        if (boom.getGames().isEmpty()) {
            return Mono.just(RAND.nextInt(4));
        }

        return boomRepository.findById(boom.getGames().getLast())
            .map(game -> (game.getDealer() + 1) % 4);

    }


    @Override
    public Mono<ResponseEntity<Game>> playBoom(final UUID appIdentifier, final String boomId, final ServerWebExchange exchange) {
        synchronized (lockMap.computeIfAbsent(boomId, _unused -> new Object())) {
            return authorize(appIdentifier, exchange)
                .doOnNext((user) -> log.info("{} playBoom() userId={} boomId={}", exchange.getRequest().getRemoteAddress(), user.getId(), boomId))
                .flatMap(this::updateUser)
                .flatMap(user -> {
                    return boomRepository.findById(boomId)
                        .map((boom) -> {
                            return Flux.fromIterable(boom.getGames())
                                .flatMap(gameRepository::findById)
                                .filter(game -> !new GameEngineImpl(game).isCompleted())
                                .next()
                                .switchIfEmpty(Mono.defer(() -> {
                                    if (boom.getGames().size() < 16) {
                                        return calcDealer(boom).flatMap(dealer -> {
                                            return gameService.createGame(user.getId(), boom.getPlayers(), boom.getGameVariant(), boom.getId(), dealer)
                                                .doOnNext(game -> boom.getGames()
                                                    .add(game.getId()))
                                                .flatMap(game -> boomRepository.save(boom)
                                                    .thenReturn(game));
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
    public Mono<ResponseEntity<Void>> deleteBoom(final UUID appIdentifier, final String boomId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} deleteBoom() userId={} boomId={}", exchange.getRequest().getRemoteAddress(), user.getId(), boomId))
            .flatMap(this::updateUser)
            .map(User::getId)
            .flatMap(boomRepository::deleteById)
            .then(Mono.just(ResponseEntity.ok().build()));
    }


}
