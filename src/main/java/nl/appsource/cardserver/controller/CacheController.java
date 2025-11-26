package nl.appsource.cardserver.controller;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.service.SseEmitterRepository;
import org.openapitools.api.ReloadCacheApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@Slf4j
public class CacheController extends GenericController implements ReloadCacheApi {

    public CacheController(final SseEmitterRepository sseEmitterRepository) {
        super(sseEmitterRepository);
    }

    @Override
    public Mono<ResponseEntity<Void>> reloadCache(final UUID appIdentifier, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} reloadCache() userId={}", exchange.getRequest().getRemoteAddress(), userId))
            .doOnNext(userId -> sseEmitterRepository.sendFlux(appIdentifier, userId))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

}
