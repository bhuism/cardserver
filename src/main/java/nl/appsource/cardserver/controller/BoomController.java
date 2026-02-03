package nl.appsource.cardserver.controller;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.BoomToOpenApiConverter;
import nl.appsource.cardserver.converter.GameToOpenApiConverter;
import nl.appsource.cardserver.repository.BoomRepository;
import nl.appsource.cardserver.repository.GameRepository;
import nl.appsource.cardserver.repository.SseSessionRepository;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.BoomService;
import nl.appsource.cardserver.service.GameEngineImpl;
import nl.appsource.cardserver.service.GameService;
import nl.appsource.cardserver.service.UserService;
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
import java.util.Random;

@RestController
@Slf4j
public class BoomController extends GenericController implements BoomApi, V1Api {

    private final BoomService boomService;

    private final BoomToOpenApiConverter boomToOpenApiConverter;

    private final BoomRepository boomRepository;

    private final GameRepository gameRepository;

    private final GameService gameService;

    private final GameToOpenApiConverter gameToOpenApiConverter;

    private final UserRepository userRepository;

    private static final Random RAND = new SecureRandom();

    public BoomController(final SseSessionRepository sseSessionRepository, final BoomService boolService, final BoomToOpenApiConverter boomToOpenApiConverter, final GameRepository gameRepository, final BoomRepository boomRepository, final GameService gameService, final GameToOpenApiConverter gameToOpenApiConverter, final UserRepository userRepository, final UserService userService) {
        super(userRepository, sseSessionRepository, userService);
        this.userRepository = userRepository;
        this.boomService = boolService;
        this.boomToOpenApiConverter = boomToOpenApiConverter;
        this.gameRepository = gameRepository;
        this.boomRepository = boomRepository;
        this.gameService = gameService;
        this.gameToOpenApiConverter = gameToOpenApiConverter;
    }

    @Override
    public Mono<ResponseEntity<Boom>> createBoom(final String appIdentifier, final Mono<CreateBoom> createBoomMono, final ServerWebExchange exchange) {
        log.info("{} createBoom() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
            .flatMap(auth -> userRepository.findById(auth.userId())
                .flatMap(
                    user -> createBoomMono.flatMap(createBoom -> boomService.createBoom(auth.userId(), createBoom.getPlayers(), user.getGameVariant(), user.getAiRisc()))
                )
            )
            .flatMap(boomToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<Boom>> getBoom(final String appIdentifier, final String boomId, final ServerWebExchange exchange) {
        log.info("{} getBoom() appIdentifier={} boomId={}", exchange.getRequest().getRemoteAddress(), appIdentifier, boomId);
        return authorize(appIdentifier, exchange)
            .flatMap(auth -> boomService.getBoom(auth.userId(), boomId))
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
    public Mono<ResponseEntity<GetBooms200Response>> getBooms(final String appIdentifier, final ServerWebExchange exchange) {
        log.info("{} getBooms() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
            .flatMap(auth -> boomService.getBooms(auth.userId())
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
    public Mono<ResponseEntity<Game>> playBoom(final String appIdentifier, final String boomId, final ServerWebExchange exchange) {
        log.info("{} playBoom() appIdentifier={} boomId={}", exchange.getRequest().getRemoteAddress(), appIdentifier, boomId);
        return authorize(appIdentifier, exchange)
            .flatMap(auth -> {
                return boomRepository.findById(boomId)
                    .map((boom) -> {
                        return Flux.fromIterable(boom.getGames())
                            .flatMap(gameRepository::findById)
                            .filter(game -> !new GameEngineImpl(game).isCompleted())
                            .next()
                            .switchIfEmpty(Mono.defer(() -> {
                                if (boom.getGames().size() < 16) {
                                    return calcDealer(boom)
                                        .flatMap(dealer -> {
                                            log.info("Boom " + boomId + ", player=" + boom.getPlayers());
                                            return gameService.createGame(auth.userId(), boom.getPlayers(), boom.getGameVariant(), boom.getId(), dealer, boom.getAiRisc())
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

    @Override
    public Mono<ResponseEntity<Void>> deleteBoom(final String appIdentifier, final String boomId, final ServerWebExchange exchange) {
        log.info("{} deleteBoom() appIdentifier={} boomId={}", exchange.getRequest().getRemoteAddress(), appIdentifier, boomId);
        return authorize(appIdentifier, exchange)
            .map(CardServerAuthentication::userId)
            .flatMap(boomRepository::deleteById)
            .then(Mono.just(ResponseEntity.ok().build()));
    }


}
