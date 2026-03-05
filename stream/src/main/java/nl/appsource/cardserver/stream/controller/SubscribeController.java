package nl.appsource.cardserver.stream.controller;

import com.couchbase.client.core.error.CasMismatchException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.stream.service.SseEmitterRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;


@Slf4j
@RestController
@RequiredArgsConstructor
public class SubscribeController implements V1Api {

    private final SseEmitterRepository sseEmitterRepository;

    private final UserRepository userRepository;

    protected Mono<String> getUserId(final ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .mapNotNull(SecurityContext::getAuthentication)
            .filter(Authentication::isAuthenticated)
            .map(Authentication::getName)
            .flatMap(userId -> userRepository.updateUpdated(userId)
                .retryWhen(Retry.backoff(5, Duration.ofMillis(100)).filter(throwable -> throwable instanceof CasMismatchException))
                .switchIfEmpty(Mono.defer(() -> {
                        log.warn("{} {} user not found, userId={}", exchange.getRequest().getRemoteAddress(), exchange.getRequest().getPath(), userId);
                        return Mono.empty();
                    })
                ));
    }

    @PostMapping(path = "/subscribe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Mono<ResponseEntity<Flux<@NonNull ServerSentEvent<@NonNull Object>>>> subscribe(final ServerWebExchange exchange) {

        final List<String> userAgentList = exchange.getRequest().getHeaders().get("User-Agent");
        final String userAgent = userAgentList != null && !userAgentList.isEmpty() ? userAgentList.getFirst() : null;

        exchange.getResponse().getHeaders().add("X-Accel-Buffering", "no");

        return getUserId(exchange)
            .doOnNext(userId -> log.info("{} subscribe() userId={}", exchange.getRequest().getRemoteAddress(), userId))
            .map(userId -> ResponseEntity.ok(sseEmitterRepository.subscribe(
                userId, "" + exchange.getRequest().getRemoteAddress(),
                userAgent
            )))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Flux.empty()));

    }

}
