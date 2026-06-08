package nl.appsource.cardserver.api.controller;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converters.service.UserToOpenApiConverter;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.generated.openapi.model.User;
import org.openapitools.api.LoadUserApi;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthController extends AbstractBaseController implements LoadUserApi, V1Api {

    private final UserToOpenApiConverter userToOpenApiConverter;
    private final UserRepository userRepository;

    @Override
    public Mono<@NonNull ResponseEntity<@NonNull User>> loadUser(final ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getDetails)
            .cast(nl.appsource.cardserver.model.User.class)
            .doOnNext(user -> user.setLastLogin(Instant.now()))
            .flatMap(userRepository::save)
            .switchIfEmpty(createUser(exchange))
            .doOnNext(user -> {
                log.info("{} loadUser(), name={} email={}", exchange.getRequest().getRemoteAddress(), user.getDisplayName(), user.getEmail());
            })
            .map(userToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    public Mono<nl.appsource.cardserver.model.User> createUser(final ServerWebExchange exchange) {

        return ReactiveSecurityContextHolder.getContext()
            .mapNotNull(SecurityContext::getAuthentication)
            .mapNotNull(Authentication::getPrincipal)
            .cast(Jwt.class)
            .flatMap(principal -> {

                final String email = principal.getClaims().get("email").toString();

                final String name = principal.getClaims().get("name").toString();

                log.info("{} createUser(), name={} email={}", exchange.getRequest().getRemoteAddress(), name, email);

                final Instant now = Instant.now();

                return userRepository.findByEmail(email).map((user) -> {
                    user.setLastLogin(now);
                    return user;
                }).switchIfEmpty(Mono.defer(() -> {

                    final String subject = principal.getSubject();

                    log.info("{} Creating a new user id={} email={}", exchange.getRequest().getRemoteAddress(), subject, email);

                    final nl.appsource.cardserver.model.User user = new nl.appsource.cardserver.model.User();

                    user.setId(subject);
                    user.setEmail(email);
                    user.setName(principal.getClaims().get("name").toString());
                    user.setDisplayName(principal.getClaims().get("name").toString());
                    user.setLastLogin(now);
                    user.setPhotoURL(principal.getClaims().get("picture") != null ? principal.getClaims().get("picture").toString() : null);
                    user.setProviderId("google");

                    return Mono.just(user);
                })).flatMap(userRepository::save);


            });

    }

}
