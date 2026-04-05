package nl.appsource.cardsever.api.controller;

import com.couchbase.client.core.error.CasMismatchException;
import com.couchbase.client.core.error.DocumentNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.couchbase.repository.SseSessionRepository;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import nl.appsource.cardserver.openapi.service.RedisPubSubService;
import nl.appsource.generated.openapi.model.PingPongSchema;
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
import java.util.Map;


@Slf4j
@RestController
@RequiredArgsConstructor
public class PingPongController extends AbstractBaseController implements V1Api, PingApi, PongApi {

    private final SseSessionRepository sseSessionRepository;
    private final RedisPubSubService redisPubSubService;
    private final JsonMapper jsonMapper;

    @Override
    public Mono<ResponseEntity<Void>> ping(final Mono<PingPongSchema> pingPongSchema, final ServerWebExchange exchange) {
//        log.info("{} ping() ", exchange.getRequest().getRemoteAddress());
        return getUserId(exchange)
            .flatMap(_ -> pingPongSchema)
            .flatMap(pingPong -> sseSessionRepository.pingReceived(pingPong.getAppIdentifier())
                .onErrorResume(DocumentNotFoundException.class, ex -> Mono.empty())
                .retryWhen(Retry.backoff(5, Duration.ofMillis(100)).filter(throwable -> throwable instanceof CasMismatchException))
                .flatMap(_ -> redisPubSubService.broadCast(pingPong.getAppIdentifier(), new MyServerSentEvent("pong", jsonMapper.writeValueAsString(Map.of("count", pingPong.getCount())))))
            )
            .map(_ -> ResponseEntity.ok().<Void>build())
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .build());
    }

    @Override
    public Mono<ResponseEntity<Void>> pong(final Mono<PingPongSchema> pingPongSchema, final ServerWebExchange exchange) {
//        log.info("{} pong()", exchange.getRequest().getRemoteAddress());
        return getUserId(exchange)
            .flatMap(_ -> pingPongSchema.map(PingPongSchema::getAppIdentifier))
            .flatMap(sseSessionRepository::pongReceived)
            .onErrorResume(DocumentNotFoundException.class, ex -> Mono.empty())
            .retryWhen(Retry.backoff(5, Duration.ofMillis(100)).filter(throwable -> throwable instanceof CasMismatchException))
            .map(_ -> ResponseEntity.ok().<Void>build())
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

}
