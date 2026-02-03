package nl.appsource.cardserver.controller;

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

@Slf4j
@RequiredArgsConstructor
public class GenericController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SseSessionRepository sseSessionRepository;

    public Mono<String> getUserId(final ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .mapNotNull(SecurityContext::getAuthentication)
            .filter(Authentication::isAuthenticated)
            .map(Authentication::getName)
            .flatMap(userId -> userRepository.updateUpdated(userId).switchIfEmpty(Mono.defer(() -> {
                    log.warn("{} {} user not found, userId={}", exchange.getRequest().getRemoteAddress(), exchange.getRequest().getPath(), userId);
                    return Mono.empty();
                })
            ));
    }

    protected Mono<CardServerAuthentication> authorize(final String appIdentifier, final ServerWebExchange exchange) {
        return getUserId(exchange)
            .flatMap(userId -> sseSessionRepository.updateUpdated(appIdentifier).map(_unused -> new CardServerAuthentication(userId, appIdentifier)))
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("{} {} session not found, appIdentifier={}", exchange.getRequest().getRemoteAddress(), exchange.getRequest().getPath(), appIdentifier);
                return Mono.empty();
            }));

    }

}
