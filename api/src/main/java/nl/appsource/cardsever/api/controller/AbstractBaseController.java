package nl.appsource.cardsever.api.controller;

import com.couchbase.client.core.error.CasMismatchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.model.BaseEntity;
import nl.appsource.cardserver.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractBaseController {

    @Autowired
    private UserRepository userRepository;

    protected Mono<String> getUserId(final ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .mapNotNull(SecurityContext::getAuthentication)
            .filter(Authentication::isAuthenticated)
            .mapNotNull(Authentication::getPrincipal)
            .filter(jwt -> jwt instanceof Jwt)
            .cast(Jwt.class)
            .flatMap(jwt -> userRepository.findById(jwt.getSubject())
                .switchIfEmpty(Mono.defer(() -> userRepository.findBySubject(jwt.getSubject())
                    .switchIfEmpty(Mono.defer(() -> Mono.just(jwt.getClaimAsString("email"))
                        .flatMap(userRepository::findByEmail)
                        .flatMap(user -> {

                            log.debug("User found by email: {}", user.getEmail());

                            return userRepository.lock(user.getId(), Duration.ofMillis(500), User.class)
                                .retryWhen(Retry.backoff(5, Duration.ofMillis(100))
                                    .doAfterRetry(retrySignal -> {
                                        log.info("Retrying lock because of: " + retrySignal.toString());
                                    }))
                                .flatMap(entry -> {
                                    return Mono.just(entry.getKey())
                                        .doOnNext(u -> u.setSubject(jwt.getSubject()))
                                        .doOnNext(u -> log.debug("Updating user subject with id: {}", u.getId()))
                                        .flatMap((a) -> userRepository.updateLocked(entry.getKey()
                                                .getId(), entry.getKey(), entry.getValue())
                                            .then(Mono.just(a)))
                                        .onErrorResume(error -> {
                                            log.error("Error during update, attempting to unlock user: {}", entry.getKey()
                                                .getId());
                                            return userRepository.unLockNoSave(entry.getKey()
                                                    .getId(), entry.getValue())
                                                // Swallow unlock-specific errors so we don't mask the original error
                                                .onErrorResume(unlockError -> {
                                                    log.warn("Failed to cleanly unlock document: {}", entry.getKey()
                                                        .getId());
                                                    return Mono.empty();
                                                })
                                                .then(Mono.error(error));
                                        })
                                        .doFinally(signalType -> {
                                            userRepository.unLockNoSave(entry.getKey()
                                                    .getId(), entry.getValue())
                                                .onErrorResume((e) -> Mono.empty())
                                                .subscribe();
                                        });
                                });
                        })))))
                .map(BaseEntity::getId)
                .flatMap(userId -> userRepository.updateUpdated(userId)
                    .retryWhen(Retry.backoff(5, Duration.ofMillis(100))
                        .filter(throwable -> throwable instanceof CasMismatchException))
                    .switchIfEmpty(Mono.defer(() -> {
                            log.warn("{} {} user not found, userId={}", exchange.getRequest()
                                .getRemoteAddress(), exchange.getRequest()
                                .getPath(), userId);
                            return Mono.empty();
                        })
                    ))
            );
    }

}
