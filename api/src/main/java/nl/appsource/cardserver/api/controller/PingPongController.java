package nl.appsource.cardserver.api.controller;

import com.couchbase.client.core.error.CasMismatchException;
import com.couchbase.client.core.error.DocumentNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.couchbase.repository.SseSessionRepository;
import nl.appsource.cardserver.model.SseSession;
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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import java.util.Set;


@Slf4j
@RestController
@RequiredArgsConstructor
public class PingPongController extends AbstractBaseController implements V1Api, PingApi, PongApi {

    private final KafkaSender kafkaSender;

    private final SseSessionRepository sseSessionRepository;

    private static final String HOSTNAME;

    static {
        String host;
        try {
            host = InetAddress.getLocalHost()
                .getHostName();
        } catch (UnknownHostException e) {
            host = "unknown";
        }
        HOSTNAME = host;
    }

    @Override
    public Mono<ResponseEntity<Void>> ping(final Mono<PingPongSchema> pingPongSchema, final ServerWebExchange exchange) {
//        log.info("{} ping() ", exchange.getRequest().getRemoteAddress());
        return getUserId(exchange)
            .doOnNext(userId -> kafkaSender.sendSse(new MyServerSentEvent<Void>("pong", Set.of(userId))))
            .flatMap(userId -> pingPongSchema.map(PingPongSchema::getAppIdentifier)
                .flatMap(appIdentifier -> sseSessionRepository.findById(appIdentifier)
                    .or(Mono.defer(() -> {
                        final List<String> userAgentList = exchange.getRequest()
                            .getHeaders()
                            .get("User-Agent");
                        final String userAgent = userAgentList != null && !userAgentList.isEmpty() ? userAgentList.getFirst() : null;
                        final String remoteAddress = "" + exchange.getRequest()
                            .getRemoteAddress();
                        return Mono.just(new SseSession(appIdentifier, remoteAddress, userAgent, HOSTNAME, userId));
                    }))
                    .doOnNext(sseSession -> {
                        sseSession.setPingReceivedCount(sseSession.getPingReceivedCount() + 1);
                        sseSession.setPingReceived(Instant.now());
                    })
                    .flatMap(sseSessionRepository::save))
            )
            .onErrorResume(CasMismatchException.class, ex -> Mono.empty())
            .onErrorResume(DocumentNotFoundException.class, ex -> Mono.empty())
            .map(_ -> ResponseEntity.ok()
                .<Void>build())
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .build());
    }

    @Override
    public Mono<ResponseEntity<Void>> pong(final Mono<PingPongSchema> pingPongSchema, final ServerWebExchange exchange) {
//        log.info("{} pong()", exchange.getRequest().getRemoteAddress());
        return getUserId(exchange)
            .flatMap(userId -> pingPongSchema.map(PingPongSchema::getAppIdentifier)
                .flatMap(appIdentifier -> sseSessionRepository.findById(appIdentifier)
                    .or(Mono.defer(() -> {
                        final List<String> userAgentList = exchange.getRequest()
                            .getHeaders()
                            .get("User-Agent");
                        final String userAgent = userAgentList != null && !userAgentList.isEmpty() ? userAgentList.getFirst() : null;
                        final String remoteAddress = "" + exchange.getRequest()
                            .getRemoteAddress();
                        return Mono.just(new SseSession(appIdentifier, remoteAddress, userAgent, HOSTNAME, userId));
                    }))
                    .doOnNext(sseSession -> {
                        sseSession.setPongReceivedCount(sseSession.getPingReceivedCount() + 1);
                        sseSession.setPongReceived(Instant.now());
                    })
                    .flatMap(sseSessionRepository::save))
            )
            .onErrorResume(CasMismatchException.class, ex -> Mono.empty())
            .onErrorResume(DocumentNotFoundException.class, ex -> Mono.empty())
            .map(_ -> ResponseEntity.ok()
                .<Void>build())
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .build());
    }

}
