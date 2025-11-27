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

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class GenericController {

    protected final SseEmitterRepository sseEmitterRepository;

    protected final UserRepository userRepository;

    public Mono<User> getUserId(final ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .flatMap(userId -> userRepository.findById(userId).doOnNext(user -> user.setUpdated(Instant.now())).flatMap(userRepository::save))
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("{} {} no authentication", exchange.getRequest()
                    .getRemoteAddress(), exchange.getRequest()
                    .getPath());
                return Mono.empty();
            }));
    }

    protected Mono<User> authorize(final UUID appIdentifier, final ServerWebExchange exchange) {
        return getUserId(exchange)
            .filter((user) -> sseEmitterRepository.validate(appIdentifier, user.getId()))
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("{} {} sseEmitterRepository validation failed", exchange.getRequest()
                    .getRemoteAddress(), exchange.getRequest()
                    .getPath());
                return Mono.empty();
            }));
    }

}
