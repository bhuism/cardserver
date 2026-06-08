package nl.appsource.cardserver.api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.api.service.BoomService;
import nl.appsource.cardserver.converters.service.BoomToOpenApiConverter;
import nl.appsource.cardserver.converters.service.GameToOpenApiConverter;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.generated.openapi.model.Boom;
import nl.appsource.generated.openapi.model.CreateBoom;
import nl.appsource.generated.openapi.model.Game;
import nl.appsource.generated.openapi.model.GetBooms200Response;
import org.openapitools.api.BoomApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.util.Random;

@RestController
@Slf4j
@RequiredArgsConstructor
public class BoomController extends AbstractBaseController implements BoomApi, V1Api {

    private final BoomService boomService;
    private final BoomToOpenApiConverter boomToOpenApiConverter;
    private final GameToOpenApiConverter gameToOpenApiConverter;
    private final UserRepository userRepository;

    private static final Random RAND = new SecureRandom();

    @Override
    public Mono<ResponseEntity<Boom>> createBoom(final Mono<CreateBoom> createBoomMono, final ServerWebExchange exchange) {
//        log.info("{} createBoom()", exchange.getRequest().getRemoteAddress());
        return getUserId(exchange)
            .flatMap(userId -> userRepository.findById(userId)
                .flatMap(
                    user -> createBoomMono.flatMap(createBoom -> boomService.createBoom(userId, createBoom.getPlayers(), user.getGameVariant(), user.getAiRisc()))
                )
            )
            .map(boomToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<Boom>> getBoom(final String boomId, final ServerWebExchange exchange) {
//        log.info("{} getBoom() boomId={}", exchange.getRequest().getRemoteAddress(), boomId);
        return getUserId(exchange)
            .flatMap(userId -> boomService.getBoom(userId, boomId))
            .map(boomToOpenApiConverter::convert)
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
    public Mono<ResponseEntity<GetBooms200Response>> getBooms(final ServerWebExchange exchange) {
//        log.info("{} getBooms()", exchange.getRequest().getRemoteAddress());
        return getUserId(exchange)
            .flatMap(userId -> boomService.getBooms(userId)
                .collectList()
                .map(booms -> new GetBooms200Response().booms(booms))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
            )
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());

    }

    @Override
    public Mono<ResponseEntity<Game>> playBoom(final String boomId, final ServerWebExchange exchange) {
        log.info("{} playBoom() boomId={}", exchange.getRequest().getRemoteAddress(), boomId);
        return getUserId(exchange)
            .doOnNext(userId -> log.info("{} playBoom() userId={} boomId={}", exchange.getRequest().getRemoteAddress(), userId, boomId))
            .flatMap(userId -> boomService.playBoom(userId, boomId))
            .mapNotNull(gameToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build())
            .map(responseEntityMono -> responseEntityMono);
    }

}
