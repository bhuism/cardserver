package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.SseSessionRepository;
import nl.appsource.cardserver.repository.UserRepository;
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

    public Mono<User> getUserId(final ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .mapNotNull(SecurityContext::getAuthentication)
            .filter(Authentication::isAuthenticated)
            .map(Authentication::getName)
            .flatMap(userId -> {
                //log.info("Looking for userId: {}", userId);
                    return userRepository.findById(userId)
                        .switchIfEmpty(Mono.defer(() -> {
                            log.warn("{} {} user not found, userId={}", exchange.getRequest()
                                .getRemoteAddress(), exchange.getRequest()
                                .getPath(), userId);
                            return Mono.empty();
                        }));
                }
            )
//            .flatMap(userRepository::save)
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("{} {} no authentication", exchange.getRequest()
                    .getRemoteAddress(), exchange.getRequest()
                    .getPath());
                return Mono.empty();
            }));
    }



    protected Mono<CardServerAuthentication> authorize(final String appIdentifier, final ServerWebExchange exchange) {
        return getUserId(exchange)
            .flatMap((user) -> sseSessionRepository.findByIdAndCreator(appIdentifier, user.getId())
//                .flatMap(sseSessionRepository::save)
                .map(sseSession -> new CardServerAuthentication(user, sseSession))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("{} {} session not found, appIdentifier={} userId={}", exchange.getRequest().getRemoteAddress(), exchange.getRequest().getPath(), appIdentifier, user.getDisplayName());
                    return Mono.empty();
                }))
            );
    }

}
