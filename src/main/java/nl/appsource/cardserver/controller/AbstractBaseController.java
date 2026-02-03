package nl.appsource.cardserver.controller;

import com.couchbase.client.core.error.CasMismatchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.repository.SseSessionRepository;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.utils.CardServerAuthentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractBaseController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SseSessionRepository sseSessionRepository;

    public Mono<String> getUserId(final ServerWebExchange exchange) {
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

    protected Mono<CardServerAuthentication> authorize(final String appIdentifier, final ServerWebExchange exchange) {
        return getUserId(exchange)
            .flatMap(userId ->
                sseSessionRepository.updateUpdated(appIdentifier)
                    .retryWhen(Retry.backoff(5, Duration.ofMillis(100)).filter(throwable -> throwable instanceof CasMismatchException))
                    .map(_unused -> new CardServerAuthentication(userId, appIdentifier))
            )
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("{} {} session not found, appIdentifier={}", exchange.getRequest().getRemoteAddress(), exchange.getRequest().getPath(), appIdentifier);
                return Mono.empty();
            }));

    }

}
