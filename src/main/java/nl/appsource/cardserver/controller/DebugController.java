package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.repository.SseSessionRepository;
import nl.appsource.cardserver.utils.Utils;
import org.openapitools.api.DebugApi;
import org.openapitools.model.SseConnection;
import org.openapitools.model.SseConnections;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
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
public class DebugController implements DebugApi, V1Api {

    private final SseSessionRepository sseSessionRepository;

    @Override
    public Mono<ResponseEntity<SseConnections>> getDebugSseConnections(final String appIdentifier, final ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .mapNotNull(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .filter(Utils::isAdmin)
            .flatMap(_s -> {

                final Flux<SseConnection> connections = sseSessionRepository.findAll()
                    .map(mySseEmitterEntry -> {
                        final SseConnection sseConnection = new SseConnection();
                        sseConnection.setId(mySseEmitterEntry.getId());
                        sseConnection.setHost(mySseEmitterEntry.getHost());
                        sseConnection.setCreated(mySseEmitterEntry.getCreated());
                        sseConnection.setUpdated(Optional.ofNullable(mySseEmitterEntry.getUpdated()));
                        sseConnection.setCreator(mySseEmitterEntry.getCreator());
                        sseConnection.setPingReceived(Optional.ofNullable(mySseEmitterEntry.getPingReceived()));
                        sseConnection.setPingReceivedCount(mySseEmitterEntry.getPingReceivedCount());
                        sseConnection.setPongReceived(Optional.ofNullable(mySseEmitterEntry.getPongReceived()));
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

//                return debugSseConnections.connections().collectList().map(connections -> {
//                    final SseConnections sseConnections = new SseConnections();
//                    sseConnections.connections(connections);
//                    sseConnections.setTimeStamp(debugSseConnections.timeStamp());
//                    sseConnections.setCurrentSubscriberCount(BigDecimal.valueOf(debugSseConnections.subscriberCount()));
//                    return sseConnections;
//                });
            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(
                ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .build()
            );
    }
}
