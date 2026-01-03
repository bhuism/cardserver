package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.SseEmitterRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class GenericController {

    protected final SseEmitterRepository sseEmitterRepository;

    protected final UserRepository userRepository;

    public Mono<User> getUserId(final ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .mapNotNull(SecurityContext::getAuthentication)
            .filter(Authentication::isAuthenticated)
            .map(Authentication::getName)
            .flatMap(userRepository::findById)
//            .flatMap(userRepository::save)
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("{} {} no authentication", exchange.getRequest()
                    .getRemoteAddress(), exchange.getRequest()
                    .getPath());
                return Mono.empty();
            }));
    }

    protected Mono<User> authorize(final UUID appIdentifier, final ServerWebExchange exchange) {
        return getUserId(exchange)
            .flatMap((user) -> sseEmitterRepository.validate(appIdentifier, user).switchIfEmpty(Mono.defer(() -> {
                    log.warn("{} {} session not found, appIdentifier={} userId={}", exchange.getRequest().getRemoteAddress(), exchange.getRequest().getPath(), appIdentifier.toString(), user.getDisplayName());
                    return Mono.empty();
                }))
            );
    }

}
