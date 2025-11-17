package nl.appsource.cardserver.controller;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.service.SseEmitterRepository;
import org.openapitools.api.SubscribeEventApi;
import org.openapitools.api.UnSubscribeEventApi;
import org.openapitools.model.SubscribeEventRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;


@Slf4j
@RestController
public class SubscribeController extends GenericController implements SubscribeEventApi, UnSubscribeEventApi {

    public static final String APP_IDENTIFIER_HEADER_NAME = "App-Identifier";

    public SubscribeController(final SseEmitterRepository sseEmitterRepository) {
        super(sseEmitterRepository);
    }

    @GetMapping(path = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<?>> subscribe(final ServerWebExchange exchange, @RequestHeader(name = APP_IDENTIFIER_HEADER_NAME) final String appIdentifier) {
        return getUserId(exchange)
            .flatMapMany(userId -> sseEmitterRepository.subscribe(UUID.fromString(appIdentifier), userId, "" + exchange.getRequest().getRemoteAddress()));
    }

    @Override
    public Mono<ResponseEntity<Void>> subscribeEvent(final UUID appIdentifier, final Mono<SubscribeEventRequest> subscribeEventRequest, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .flatMap((userId) -> subscribeEventRequest.doOnNext(entityEventRequest -> {
                    log.info("{} subscribeEvent() userId={} topics={}", exchange.getRequest().getRemoteAddress(), userId, entityEventRequest.getTopics());
                    sseEmitterRepository.eventSubscribe(appIdentifier, entityEventRequest.getTopics());
                }))
            .<ResponseEntity<Void>>then(Mono.just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<Void>> unSubscribeEvent(final UUID appIdentifier, final Mono<SubscribeEventRequest> subscribeEventRequest, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .flatMap((userId) -> subscribeEventRequest.doOnNext(entityEventRequest -> {
                    log.info("{} unSubscribeEvent() userId={} topics={}", exchange.getRequest().getRemoteAddress(), userId, entityEventRequest.getTopics());
                    sseEmitterRepository.eventUnSubscribe(appIdentifier, entityEventRequest.getTopics());
                }))
                .<ResponseEntity<Void>>then(Mono.just(ResponseEntity.ok().build()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

}
