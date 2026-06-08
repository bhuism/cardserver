package nl.appsource.cardserver.stream.controller;

import com.couchbase.client.core.error.CasMismatchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
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
                .switchIfEmpty(Mono.defer(() -> userRepository.findBySubject(jwt.getSubject())))
            )
            .map(User::getId)
            .flatMap(userId -> userRepository.updateUpdated(userId)
                .retryWhen(Retry.backoff(5, Duration.ofMillis(100)).filter(throwable -> throwable instanceof CasMismatchException))
                .switchIfEmpty(Mono.defer(() -> {
                        log.warn("{} {} user not found, userId={}", exchange.getRequest().getRemoteAddress(), exchange.getRequest().getPath(), userId);
                        return Mono.empty();
                    })
                ));
    }

}
