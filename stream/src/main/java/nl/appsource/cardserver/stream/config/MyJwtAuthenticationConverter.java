package nl.appsource.cardserver.stream.config;

import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class MyJwtAuthenticationConverter implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    private final UserRepository userRepository;

    private final ReactiveJwtAuthenticationConverter delegate = new ReactiveJwtAuthenticationConverter();

    @Override
    public Mono<AbstractAuthenticationToken> convert(final Jwt source) {
        return delegate.convert(source)
            .flatMap(abstractAuthenticationToken ->
                Mono.justOrEmpty(source.getSubject())
                    .flatMap(subject -> userRepository.findById(subject).switchIfEmpty(Mono.defer((() -> userRepository.findBySubject(subject)))))
                    .doOnNext(abstractAuthenticationToken::setDetails)
                    .then(Mono.just(abstractAuthenticationToken))
            );
    }

}
