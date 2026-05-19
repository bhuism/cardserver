package nl.appsource.cardsever.api.controller;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converters.service.UserToOpenApiConverter;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardsever.api.service.UserService;
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

import static nl.appsource.cardserver.utils.IDTYPE.USER;
import static nl.appsource.cardserver.utils.Utils.idGen;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthController extends AbstractBaseController implements LoadUserApi, V1Api {

    private final UserToOpenApiConverter userToOpenApiConverter;
    private final UserRepository userRepository;
    private final UserService userService;

    @Override
    public Mono<@NonNull ResponseEntity<@NonNull User>> loadUser(final ServerWebExchange exchange) {
        return getUserId(exchange)
            .flatMap(userRepository::findById)
            .doOnNext(user -> user.setLastLogin(Instant.now()))
            .flatMap(userRepository::save)
            .map(userToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    public Mono<User> createUser(final ServerWebExchange exchange) {

        return ReactiveSecurityContextHolder.getContext()
            .mapNotNull(SecurityContext::getAuthentication)
            .mapNotNull(Authentication::getPrincipal)
            .cast(Jwt.class)
            .flatMap(principal -> {

                final String email = principal.getClaims().get("email").toString();

                final String name = principal.getClaims().get("name").toString();

                log.info("{} login(), name={} email={}", exchange.getRequest().getRemoteAddress(), name, email);

                final Instant now = Instant.now();

                return userService.findByEmail(email).map((user) -> {
                        user.setLastLogin(now);
                        return user;
                    }).switchIfEmpty(Mono.defer(() -> {

                        log.info("{} Creating a new user {}", exchange.getRequest().getRemoteAddress(), email);

                        final nl.appsource.cardserver.model.User user = new nl.appsource.cardserver.model.User();

                        user.setId(idGen(USER, 28));
                        user.setEmail(email);
                        user.setName(principal.getClaims().get("name").toString());
                        user.setDisplayName(principal.getClaims().get("name").toString());
                        user.setLastLogin(now);
                        user.setPhotoURL(principal.getClaims().get("picture") != null ? principal.getClaims().get("picture").toString() : null);
                        user.setProviderId("google");

                        return Mono.just(user);
                    })).flatMap(userRepository::save)
                    .mapNotNull(userToOpenApiConverter::convert);


            });

    }

}
