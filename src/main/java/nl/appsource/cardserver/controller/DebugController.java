package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.service.SseEmitterRepository;
import nl.appsource.cardserver.utils.Utils;
import org.openapitools.api.DebugApi;
import org.openapitools.model.SseConnections;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class DebugController implements DebugApi, V1Api {

    private final SseEmitterRepository sseEmitterRepository;

    @Override
    public Mono<ResponseEntity<SseConnections>> getDebugSseConnections(final UUID appIdentifier, final ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .filter(Utils::isAdmin)
            .map(_s -> sseEmitterRepository.getDebugSseConnections())
            .map(ResponseEntity::ok)
            .defaultIfEmpty(
                ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .build()
            );
    }
}
