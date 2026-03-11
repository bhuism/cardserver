package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.couchbase.repository.SseSessionRepository;
import nl.appsource.cardserver.utils.Utils;
import nl.appsource.generated.openapi.model.SseConnection;
import nl.appsource.generated.openapi.model.SseConnections;
import org.openapitools.api.DebugApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@Slf4j
public class DebugController extends AbstractBaseController implements DebugApi, V1Api {

    private final SseSessionRepository sseSessionRepository;

    @Override
    public Mono<ResponseEntity<SseConnections>> getDebugSseConnections(final ServerWebExchange exchange) {
        return getUserId(exchange)
            .filter(Utils::isAdmin)
            .flatMap(_ -> {
                final Flux<SseConnection> connections = sseSessionRepository.findAll()
                    .map(mySseEmitterEntry -> {
                        final SseConnection sseConnection = new SseConnection();
                        sseConnection.setId(mySseEmitterEntry.getId());
                        sseConnection.setHost(mySseEmitterEntry.getHost());
                        sseConnection.setCreated(mySseEmitterEntry.getCreated());
                        sseConnection.setUpdated(mySseEmitterEntry.getUpdated());
                        sseConnection.setCreator(mySseEmitterEntry.getCreator());
                        sseConnection.setPingReceived(mySseEmitterEntry.getPingReceived());
                        sseConnection.setPingReceivedCount(mySseEmitterEntry.getPingReceivedCount());
                        sseConnection.setPongReceived(mySseEmitterEntry.getPongReceived());
                        sseConnection.setPongReceivedCount(mySseEmitterEntry.getPongReceivedCount());
                        sseConnection.setRemoteAddress(mySseEmitterEntry.getRemoteAddress());
                        sseConnection.setUserAgent(mySseEmitterEntry.getUserAgent());
                        return sseConnection;
                    });
                return Mono.zip(arr -> new SseConnections().connections(
                        (List<SseConnection>) arr[0]).timeStamp((Instant) arr[1]),
                    connections.collectList(),
                    Mono.just(Instant.now())
                );

            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(
                ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .build()
            );
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteSseSession(final String sessionIdentifier, final ServerWebExchange exchange) {
        return getUserId(exchange)
            .filter(Utils::isAdmin)
            .flatMap(_s -> sseSessionRepository.deleteById(sessionIdentifier).thenReturn(true))
            .map(_s -> ResponseEntity.ok().<Void>build())
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}

