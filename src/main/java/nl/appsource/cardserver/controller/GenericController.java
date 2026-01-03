package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.SseSessionRepository;
import nl.appsource.cardserver.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static nl.appsource.cardserver.utils.IDTYPE.SESS;

@Slf4j
@RequiredArgsConstructor
public class GenericController {

    private final UserRepository userRepository;

    private final SseSessionRepository sseSessionRepository;

    public Mono<User> getUserId(final ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .mapNotNull(SecurityContext::getAuthentication)
            .filter(Authentication::isAuthenticated)
            .map(Authentication::getName)
            .flatMap(userId -> userRepository.findById(userId).switchIfEmpty(Mono.defer(() -> {
                    log.warn("{} {} user not found, userId={}", exchange.getRequest().getRemoteAddress(), exchange.getRequest().getPath(), userId);
                    return Mono.empty();
                }))
            )
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
            .flatMap((user) -> {
                return sseSessionRepository.findByIdAndCreator(SESS.getIdentifier() + appIdentifier.toString(), user.getId())
                    .map(_ -> user)
                    .switchIfEmpty(Mono.defer(() -> {
                        log.warn("{} {} session not found, appIdentifier={} userId={}", exchange.getRequest().getRemoteAddress(), exchange.getRequest().getPath(), appIdentifier.toString(), user.getDisplayName());
                        return Mono.empty();
                    })
                    );

            });
    }

}
