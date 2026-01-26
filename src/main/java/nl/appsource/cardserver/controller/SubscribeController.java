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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;


@Slf4j
@RestController
public class SubscribeController extends GenericController implements V1Api {

    public static final String APP_IDENTIFIER_HEADER_NAME = "App-Identifier";

    private final SseEmitterRepository sseEmitterRepository;

    public SubscribeController(final SseEmitterRepository sseEmitterRepository, final UserRepository userRepository, final SseSessionRepository sseSessionRepository) {
        super(userRepository, sseSessionRepository);
        this.sseEmitterRepository = sseEmitterRepository;
    }

    @RequestMapping(method = {GET, POST}, path = "/subscribe/{appIdentifier}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<@NonNull ServerSentEvent<@NonNull Object>>> subscribe(final ServerWebExchange exchange, @PathVariable final String appIdentifier) {

        final List<String> userAgentList = exchange.getRequest().getHeaders().get("user-agent");
        final String userAgent = userAgentList != null && userAgentList.isEmpty() ? userAgentList.getFirst() : null;

        exchange.getResponse().getHeaders().add("X-Accel-Buffering", "no");

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noCache())
            .body(getUserId(exchange)
                .flatMapMany(user -> sseEmitterRepository.subscribe(appIdentifier,
                    user.getId(), "" + exchange.getRequest().getRemoteAddress(),
                    userAgent
                )));
    }

}
