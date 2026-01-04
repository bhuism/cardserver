package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.service.SseEmitterRepository;
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
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class DebugController implements DebugApi, V1Api {

    private final SseEmitterRepository sseEmitterRepository;

    @Override
    public Mono<ResponseEntity<SseConnections>> getDebugSseConnections(final String appIdentifier, final ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .mapNotNull(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .filter(Utils::isAdmin)
            .flatMap(_s -> {

                final var debugSseConnections = sseEmitterRepository.getDebugSseConnections();

                return Mono.zip(arr -> new SseConnections().connections(
                    (List<SseConnection>) arr[0]).timeStamp((Instant) arr[1]),
                    debugSseConnections.connections().collectList(),
                    Mono.just(debugSseConnections.timeStamp())
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
