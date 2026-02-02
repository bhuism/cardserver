package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.repository.SseSessionRepository;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.UserService;
import nl.appsource.cardserver.utils.CardServerAuthentication;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public class GenericController {

    private final UserRepository userRepository;

    private final SseSessionRepository sseSessionRepository;

    private final UserService userService;

    public Mono<String> getUserId(final ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .mapNotNull(SecurityContext::getAuthentication)
            .filter(Authentication::isAuthenticated)
            .map(Authentication::getName)
            .flatMap(userService::updateUpdated);
    }


    protected Mono<CardServerAuthentication> authorize(final String appIdentifier, final ServerWebExchange exchange) {
        return getUserId(exchange)
            .flatMap(userId -> userService.updateUpdated(appIdentifier).map(_unused -> new CardServerAuthentication(userId, appIdentifier)))
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("{} {} user not found, appIdentifier={}", exchange.getRequest().getRemoteAddress(), exchange.getRequest().getPath(), appIdentifier);
                return Mono.empty();
            }));

    }

}
