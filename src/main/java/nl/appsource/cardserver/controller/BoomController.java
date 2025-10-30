package nl.appsource.cardserver.controller;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.BoomToOpenApiConverter;
import nl.appsource.cardserver.service.BoomService;
import nl.appsource.cardserver.service.SseEmitterRepository;
import org.openapitools.api.BoomApi;
import org.openapitools.model.Boom;
import org.openapitools.model.CreateBoom;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@Slf4j
public class BoomController extends GenericController implements BoomApi {

    private final BoomService boomService;

    private final BoomToOpenApiConverter boomToOpenApiConverter;

    public BoomController(final SseEmitterRepository sseEmitterRepository, final BoomService boolServiceArg, final BoomToOpenApiConverter boomToOpenApiConverterArg) {
        super(sseEmitterRepository);
        this.boomService = boolServiceArg;
        this.boomToOpenApiConverter = boomToOpenApiConverterArg;
    }

    @Override
    public Mono<ResponseEntity<Boom>> createBoom(final UUID appIdentifier, final Mono<CreateBoom> createBoomMono, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} createBoom() userId={}", exchange.getRequest().getRemoteAddress(), userId))
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
        return null;
    }
}
