package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.service.SseEmitterRepository;
import org.openapitools.api.DebugApi;
import org.openapitools.model.GetDebugSseConnections200Response;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@Slf4j
public class DebugController implements DebugApi, V1Api {

    private final SseEmitterRepository sseEmitterRepository;

    @Override
    public Mono<ResponseEntity<GetDebugSseConnections200Response>> getDebugSseConnections(ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.ok(sseEmitterRepository.getDebugSseConnections()));
    }
}
