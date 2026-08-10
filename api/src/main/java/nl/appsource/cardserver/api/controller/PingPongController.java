package nl.appsource.cardserver.api.controller;

import com.couchbase.client.core.error.CasMismatchException;
import com.couchbase.client.core.error.DocumentNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.couchbase.repository.SseSessionRepository;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import nl.appsource.cardserver.openapi.service.KafkaSender;
import nl.appsource.generated.openapi.model.PingPongSchema;
import org.openapitools.api.PingApi;
import org.openapitools.api.PongApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;


@Slf4j
@RestController
@RequiredArgsConstructor
public class PingPongController extends AbstractBaseController implements V1Api, PingApi, PongApi {

    private final KafkaSender kafkaSender;

    private final SseSessionRepository sseSessionRepository;

    @Override
    public Mono<ResponseEntity<Void>> ping(final Mono<PingPongSchema> pingPongSchema, final ServerWebExchange exchange) {
//        log.info("{} ping() ", exchange.getRequest().getRemoteAddress());
        return getUserId(exchange)
            .doOnNext(userId -> kafkaSender.sendSse(new MyServerSentEvent<Void>("pong", Set.of(userId))))
            .flatMap(_ -> pingPongSchema.map(PingPongSchema::getAppIdentifier))
            .delayUntil(sseSessionRepository::pingReceived)
            .onErrorResume(CasMismatchException.class, ex -> Mono.empty())
            .onErrorResume(DocumentNotFoundException.class, ex -> Mono.empty())
            .map(_ -> ResponseEntity.ok().<Void>build())
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .build());
    }

    @Override
    public Mono<ResponseEntity<Void>> pong(final Mono<PingPongSchema> pingPongSchema, final ServerWebExchange exchange) {
//        log.info("{} pong()", exchange.getRequest().getRemoteAddress());
        return getUserId(exchange)
            .flatMap(_ -> pingPongSchema.map(PingPongSchema::getAppIdentifier))
            .delayUntil(sseSessionRepository::pongReceived)
            .onErrorResume(CasMismatchException.class, ex -> Mono.empty())
            .onErrorResume(DocumentNotFoundException.class, ex -> Mono.empty())
            .map(_ -> ResponseEntity.ok().<Void>build())
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

}
