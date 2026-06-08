package nl.appsource.cardserver.api.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.model.User;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Slf4j
@RequiredArgsConstructor
public class MyJwtAuthenticationConverter implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    private final UserRepository userRepository;

    private final ReactiveJwtAuthenticationConverter delegate = new ReactiveJwtAuthenticationConverter();

    @Override
    public Mono<AbstractAuthenticationToken> convert(final Jwt source) {
        return delegate.convert(source)
            .flatMap(abstractAuthenticationToken ->
                Mono.justOrEmpty(source)
                    .flatMap(jwt -> userRepository.findById(jwt.getSubject())
                        .switchIfEmpty(Mono.defer(() -> userRepository.findBySubject(jwt.getSubject())
                            .switchIfEmpty(Mono.defer(() -> userRepository.findByEmail(jwt.getClaimAsString("email")))
                                //.flatMap(userRepository::findByEmail)
                                .flatMap(user -> {

                                    log.debug("User found by email: {}", user.getEmail());

                                    return userRepository.lock(user.getId(), Duration.ofMillis(500), User.class)
                                        .retryWhen(Retry.backoff(5, Duration.ofMillis(100))
                                            .doAfterRetry(retrySignal -> {
                                                log.info("Retrying lock because of: " + retrySignal.toString());
                                            }))
                                        .flatMap(entry -> Mono.just(entry.getKey())
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
                                            }));
                                })))))
//                    .flatMap((user) -> userRepository.lock(user.getId(), Duration.ofMillis(500), User.class))
//                    .flatMap(entry -> userRepository.updateUpdated(entry.getKey().getId())
//                        .flatMap((a) -> userRepository.updateLocked(entry.getKey().getId(), entry.getKey(), entry.getValue()))
//                        .doOnNext(mutationResult -> userRepository.unLockNoSave(entry.getKey().getId(), entry.getValue()))
//                    )
//                    .flatMap(user -> userRepository.updateUpdated(user.getId())
                    .map(User::getId)
                    .doOnNext(abstractAuthenticationToken::setDetails)
                    .then(Mono.just(abstractAuthenticationToken))
            );
    }

}
