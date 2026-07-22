package nl.appsource.cardserver.api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;


@Slf4j
@RestController
@RequiredArgsConstructor
public class PingPongController extends AbstractBaseController implements V1Api, PingApi, PongApi {

    private final JsonMapper jsonMapper;

    private final KafkaSender kafkaSender;

    @Override
    public Mono<ResponseEntity<Void>> ping(final Mono<PingPongSchema> pingPongSchema, final ServerWebExchange exchange) {
//        log.info("{} ping() ", exchange.getRequest().getRemoteAddress());
        return getUserId(exchange)
//            .flatMap(userId -> pingPongSchema)
            .doOnNext(userId -> kafkaSender.sendSse(new MyServerSentEvent<Void>("pong", Set.of(userId))))
            .map(_ -> ResponseEntity.ok().<Void>build())
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .build());
    }

    @Override
    public Mono<ResponseEntity<Void>> pong(final Mono<PingPongSchema> pingPongSchema, final ServerWebExchange exchange) {
//        log.info("{} pong()", exchange.getRequest().getRemoteAddress());
//        return getUserId(exchange)
//            .flatMap(_ -> pingPongSchema.map(PingPongSchema::getAppIdentifier))
//            .flatMap(sseSessionRepository::pongReceived)
//            .onErrorResume(DocumentNotFoundException.class, ex -> Mono.empty())
//            .retryWhen(Retry.backoff(5, Duration.ofMillis(100)).filter(throwable -> throwable instanceof CasMismatchException))
//            .map(_ -> ResponseEntity.ok().<Void>build())
//            .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        return Mono.just(ResponseEntity.ok().build());
    }

}
