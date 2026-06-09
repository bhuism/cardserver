package nl.appsource.cardserver.stream.controller;

import io.micrometer.observation.annotation.Observed;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.stream.service.SseEmitterRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;


@Slf4j
@RestController
@RequiredArgsConstructor
public class SubscribeController extends AbstractBaseController  implements V1Api {

    private final SseEmitterRepository sseEmitterRepository;

    private final UserRepository userRepository;

    @Observed(name = "stream.subscribe")
    @PostMapping(path = "/subscribe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Mono<ResponseEntity<Flux<@NonNull ServerSentEvent<@NonNull Object>>>> subscribe(final ServerWebExchange exchange) {

        final List<String> userAgentList = exchange.getRequest().getHeaders().get("User-Agent");
        final String userAgent = userAgentList != null && !userAgentList.isEmpty() ? userAgentList.getFirst() : null;

        exchange.getResponse().getHeaders().add("X-Accel-Buffering", "no");

        return getUserId(exchange)
            .map(userId -> ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(sseEmitterRepository.subscribe(
                    userId, "" + exchange.getRequest().getRemoteAddress(),
                    userAgent
                )))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Flux.empty()));

    }

}
