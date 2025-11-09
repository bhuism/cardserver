package nl.appsource.cardserver.controller;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.BoomToOpenApiConverter;
import nl.appsource.cardserver.repository.BoomRepository;
import nl.appsource.cardserver.repository.GameRepository;
import nl.appsource.cardserver.service.BoomService;
import nl.appsource.cardserver.service.GameEngineImpl;
import nl.appsource.cardserver.service.GameService;
import nl.appsource.cardserver.service.SseEmitterRepository;
import org.openapitools.api.BoomApi;
import org.openapitools.model.Boom;
import org.openapitools.model.CreateBoom;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@RestController
@Slf4j
public class BoomController extends GenericController implements BoomApi {

    private final BoomService boomService;

    private final BoomToOpenApiConverter boomToOpenApiConverter;

    private final BoomRepository boomRepository;

    private final GameRepository gameRepository;

    private final GameService gameService;

    public BoomController(final SseEmitterRepository sseEmitterRepository, final BoomService boolServiceArg, final BoomToOpenApiConverter boomToOpenApiConverterArg, final GameRepository gameRepositoryArg, final BoomRepository boomRepositoryArg, final GameService gameServiceArg) {
        super(sseEmitterRepository);
        this.boomService = boolServiceArg;
        this.boomToOpenApiConverter = boomToOpenApiConverterArg;
        this.gameRepository = gameRepositoryArg;
        this.boomRepository = boomRepositoryArg;
        this.gameService = gameServiceArg;
    }

    @Override
    public Mono<ResponseEntity<Boom>> createBoom(final UUID appIdentifier, final Mono<CreateBoom> createBoomMono, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} createBoom() userId={}", exchange.getRequest()
                .getRemoteAddress(), userId))
            .flatMap(userId -> createBoomMono.flatMap(createBoom -> boomService.createBoom(userId, createBoom.getPlayers())))
            .mapNotNull(boomToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound()
                .build());
    }

    @Override
    public Mono<ResponseEntity<Boom>> getBoom(final UUID appIdentifier, final String boomId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} getBoom() userId={} boomId={}", exchange.getRequest()
                .getRemoteAddress(), userId, boomId))
            .flatMap(userId -> boomService.getBoom(userId, boomId))
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
            .doOnNext((userId) -> log.info("{} getBooms() userId={}", exchange.getRequest()
                .getRemoteAddress(), userId))
            .mapNotNull(userId -> boomService.getBooms(userId)
                .mapNotNull(boomToOpenApiConverter::convert))
            .mapNotNull(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound()
                .build());
    }

    @Override
    public Mono<ResponseEntity<Void>> playBoom(final UUID appIdentifier, final String boomId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} getBoom() userId={} boomId={}", exchange.getRequest()
                .getRemoteAddress(), userId, boomId))
            .flatMap(userId -> {
                return boomRepository.findById(boomId)
                    .map((boom) -> {
                        return Flux.fromIterable(boom.getGames())
                            .flatMap(gameRepository::findById)
                            .filter(game -> !new GameEngineImpl(game).isCompleted())
                            .next()
                            .switchIfEmpty(Mono.defer(() -> {
                                if (boom.getGames()
                                    .size() < 32) {
                                    return gameService.createGame(userId, boom.getPlayers(), boom.getId())
                                        .doOnNext(game -> boom.getGames()
                                            .add(game.getId()))
                                        .flatMap(game -> boomRepository.save(boom).thenReturn(game));
                                        //.zipWith(boomRepository.save(boom))
                                        //.map(Tuple2::getT1)
                                } else {
                                    return Mono.empty();
                                }
                            }))
                            .map(game -> new ResponseEntity<Void>(new HttpHeaders(MultiValueMap.fromSingleValue(Map.of("Location", "https://klaversjassen.nl/gamePlay/" + game.getId()))), HttpStatus.FOUND))
                            .defaultIfEmpty(ResponseEntity.notFound()
                                .build());
                    });
            })
            .flatMap(responseEntityMono -> responseEntityMono);

    }
}
