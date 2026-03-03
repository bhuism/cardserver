package nl.appsource.cardserver.controller;

import com.couchbase.client.core.error.CasMismatchException;
import com.couchbase.client.core.error.DocumentNotFoundException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.couchbase.repository.SseSessionRepository;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import nl.appsource.cardserver.openapi.service.RedisPublisher;
import nl.appsource.cardserver.utils.CardServerAuthentication;
import org.openapitools.api.PingApi;
import org.openapitools.api.PongApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;


@Slf4j
@RestController
@RequiredArgsConstructor
public class PingPongController extends AbstractBaseController implements V1Api, PingApi, PongApi {

    private final SseSessionRepository sseSessionRepository;
    private final RedisPublisher redisPublisher;
    private final JsonMapper jsonMapper;

    @Override
    public Mono<@NonNull ResponseEntity<@NonNull Void>> ping(final String appIdentifier, final ServerWebExchange exchange) {
        log.info("{} ping() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
            .map(CardServerAuthentication::appIdentifier)
            .flatMap(sseSessionRepository::pingReceived)
            .onErrorResume(DocumentNotFoundException.class, ex -> Mono.empty())
            .retryWhen(Retry.backoff(5, Duration.ofMillis(100)).filter(throwable -> throwable instanceof CasMismatchException))
            .flatMap(_ -> redisPublisher.publish(appIdentifier, jsonMapper.writeValueAsString(MyServerSentEvent.pong())))
            .map(_ -> ResponseEntity.ok().<Void>build())
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> pong(final String appIdentifier, final ServerWebExchange exchange) {
        log.info("{} pong() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
            .map(CardServerAuthentication::appIdentifier)
            .flatMap(sseSessionRepository::pongReceived)
            .onErrorResume(DocumentNotFoundException.class, ex -> Mono.empty())
            .retryWhen(Retry.backoff(5, Duration.ofMillis(100)).filter(throwable -> throwable instanceof CasMismatchException))
            .map(_ -> ResponseEntity.ok().<Void>build())
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

}
