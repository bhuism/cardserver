package nl.appsource.cardserver.controller;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.repository.SseSessionRepository;
import nl.appsource.cardserver.service.SseEmitterRepository;
import nl.appsource.cardserver.service.SseEventSender;
import nl.appsource.cardserver.utils.CardServerAuthentication;
import org.openapitools.api.PingApi;
import org.openapitools.api.PongApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;


@Slf4j
@RestController
@RequiredArgsConstructor
public class SubscribeController extends AbstractBaseController implements V1Api, PingApi, PongApi {

    private final SseEmitterRepository sseEmitterRepository;
    private final SseSessionRepository sseSessionRepository;
    private final SseEventSender sseEventSender;

    @PostMapping(path = "/subscribe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Mono<ResponseEntity<Flux<@NonNull ServerSentEvent<@NonNull Object>>>> subscribe(@RequestBody final String body, final ServerWebExchange exchange) {

        final List<String> userAgentList = exchange.getRequest().getHeaders().get("User-Agent");
        final String userAgent = userAgentList != null && !userAgentList.isEmpty() ? userAgentList.getFirst() : null;

        exchange.getResponse().getHeaders().add("X-Accel-Buffering", "no");

        return getUserId(exchange)
            .map(userId -> ResponseEntity.ok(sseEmitterRepository.subscribe(
                userId, "" + exchange.getRequest().getRemoteAddress(),
                userAgent
            )))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Flux.empty()));

    }

    @Override
    public Mono<@NonNull ResponseEntity<@NonNull Void>> ping(final String appIdentifier, final ServerWebExchange exchange) {
//        log.info("{} ping() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
            .map(CardServerAuthentication::appIdentifier)
            .flatMap(sseSessionRepository::ping)
            .delayUntil(sseEventSender::sendPong)
            .map(_ -> ResponseEntity.ok().<Void>build())
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> pong(final String appIdentifier, final ServerWebExchange exchange) {
//        log.info("{} pong() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
            .map(CardServerAuthentication::appIdentifier)
            .flatMap(sseSessionRepository::pong)
            .map(_ -> ResponseEntity.ok().<Void>build())
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

}
