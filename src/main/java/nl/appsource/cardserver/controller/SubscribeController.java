package nl.appsource.cardserver.controller;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.repository.SseSessionRepository;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.SseEmitterRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import java.util.List;


@Slf4j
@RestController
public class SubscribeController extends GenericController implements V1Api {

    private final SseEmitterRepository sseEmitterRepository;

    public SubscribeController(final SseEmitterRepository sseEmitterRepository, final UserRepository userRepository, final SseSessionRepository sseSessionRepository) {
        super(userRepository, sseSessionRepository);
        this.sseEmitterRepository = sseEmitterRepository;
    }

    @PostMapping(path = "/subscribe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<@NonNull ServerSentEvent<@NonNull Object>>> subscribe(@RequestBody final String body, final ServerWebExchange exchange) {

        final List<String> userAgentList = exchange.getRequest().getHeaders().get("User-Agent");
        final String userAgent = userAgentList != null && !userAgentList.isEmpty() ? userAgentList.getFirst() : null;

        exchange.getResponse().getHeaders().add("X-Accel-Buffering", "no");

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noCache())
            .body(getUserId(exchange)
                .flatMapMany(user -> sseEmitterRepository.subscribe(
                    user.getId(), "" + exchange.getRequest().getRemoteAddress(),
                    userAgent
                )));
    }

}
