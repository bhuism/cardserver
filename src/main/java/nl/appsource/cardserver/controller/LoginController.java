package nl.appsource.cardserver.controller;

import com.nimbusds.jose.JOSEException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.UserToOpenApiConverter;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.CardServerJwtModem;
import nl.appsource.cardserver.service.UserService;
import org.openapitools.api.LoadUserApi;
import org.openapitools.api.LoginApi;
import org.openapitools.model.GameVariant;
import org.openapitools.model.LoginResponse;
import org.openapitools.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static java.util.Collections.emptyList;
import static nl.appsource.cardserver.service.GameServiceImpl.idGen;

@RestController
@RequiredArgsConstructor
@Slf4j
public class LoginController implements LoginApi, LoadUserApi {

    private final UserService userService;

    private final CardServerJwtModem cardServerJwtModem;
    private final UserToOpenApiConverter userToOpenApiConverter;
    private final UserRepository userRepository;

    @Override
    public Mono<ResponseEntity<LoginResponse>> login(final ServerWebExchange exchange) {

        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getPrincipal)
            .map(Jwt.class::cast)
            .flatMap(principal -> {

                final String email = principal.getClaims()
                    .get("email")
                    .toString();

                log.info("{} whoampi(), email={}", exchange.getRequest()
                    .getRemoteAddress(), email);

                final Instant now = Instant.now();

                return userService.findByEmail(email)
                    .map((user) -> {

                        log.info("User login {}", user.getDisplayName());

                        user.setLastLogin(now);
                        user.setUpdated(now);
                        return user;
                    })
                    .switchIfEmpty(Mono.defer(() -> {

                        log.info("{} Creating a new user {}", exchange.getRequest()
                            .getRemoteAddress(), email);

                        final nl.appsource.cardserver.model.User user = new nl.appsource.cardserver.model.User();

                        user.setId(idGen(28));
                        user.setEmail(email);
                        user.setCreated(now);
                        user.setUpdated(now);
                        user.setName(principal.getClaims()
                            .get("name")
                            .toString());
                        user.setDisplayName(principal.getClaims()
                            .get("name")
                            .toString());
                        user.setInvites(emptyList());
                        user.setLastLogin(now);
                        user.setPhotoURL(principal.getClaims()
                            .get("picture") != null ? principal.getClaims()
                            .get("picture")
                            .toString() : null);
                        user.setProviderId("google");
                        user.setSkipAnimation(false);
                        user.setGameVariant(GameVariant.ROTTERDAMS);

                        return Mono.just(user);
                    }))
                    .flatMap(userService::save)
                    .mapNotNull(userToOpenApiConverter::convert)
                    .flatMap(
                        (user) -> {
                            try {
                                return Mono.just(new LoginResponse()
                                    .user(user)
                                    .jwt(cardServerJwtModem.encode(user.getId())
                                        .serialize()));
                            } catch (JOSEException e) {
                                return Mono.error(e);
                            }
                        }
                    )
                    .map(ResponseEntity::ok);

            });

    }

    private Mono<String> getUserId(final ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("{} {} no authentication", exchange.getRequest()
                    .getRemoteAddress(), exchange.getRequest()
                    .getPath());
                return Mono.empty();
            }));
    }

    @Override
    public Mono<ResponseEntity<User>> loadUser(final ServerWebExchange exchange) {
        return getUserId(exchange)
            .flatMap(userRepository::findById)
            .mapNotNull(userToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
