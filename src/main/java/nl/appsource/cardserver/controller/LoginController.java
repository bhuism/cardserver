package nl.appsource.cardserver.controller;

import com.nimbusds.jose.JOSEException;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.UserToOpenApiConverter;
import nl.appsource.cardserver.repository.SseSessionRepository;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.CardServerJwtModem;
import nl.appsource.cardserver.service.UserService;
import org.openapitools.api.LoginApi;
import org.openapitools.model.LoginResponse;
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
public class LoginController implements LoginApi {

    private final UserService userService;
    private final CardServerJwtModem cardServerJwtModem;
    private final UserToOpenApiConverter userToOpenApiConverter;
    private final UserRepository userRepository;

    public LoginController(final UserRepository userRepository, final UserService userService, final CardServerJwtModem cardServerJwtModem, final UserToOpenApiConverter userToOpenApiConverterArg, final SseSessionRepository sseSessionRepository) {
        this.userService = userService;
        this.cardServerJwtModem = cardServerJwtModem;
        this.userToOpenApiConverter = userToOpenApiConverterArg;
        this.userRepository = userRepository;
    }

    @Override
    public Mono<ResponseEntity<LoginResponse>> login(final ServerWebExchange exchange) {

        return ReactiveSecurityContextHolder.getContext()
            .mapNotNull(SecurityContext::getAuthentication)
            .mapNotNull(Authentication::getPrincipal)
            .map(Jwt.class::cast)
            .flatMap(principal -> {

                final String email = principal.getClaims()
                    .get("email")
                    .toString();

                final String name = principal.getClaims()
                    .get("name")
                    .toString();

                log.info("{} login(), name={} email={}", exchange.getRequest().getRemoteAddress(), name, email);

                final Instant now = Instant.now();

                return userService.findByEmail(email)
                    .map((user) -> {
                        user.setLastLogin(now);
                        return user;
                    })
                    .switchIfEmpty(Mono.defer(() -> {

                        log.info("{} Creating a new user {}", exchange.getRequest()
                            .getRemoteAddress(), email);

                        final nl.appsource.cardserver.model.User user = new nl.appsource.cardserver.model.User();

                        user.setId(idGen(USER, 28));
                        user.setEmail(email);
                        user.setName(principal.getClaims()
                            .get("name")
                            .toString());
                        user.setDisplayName(principal.getClaims()
                            .get("name")
                            .toString());
                        user.setLastLogin(now);
                        user.setPhotoURL(principal.getClaims()
                            .get("picture") != null ? principal.getClaims()
                            .get("picture")
                            .toString() : null);
                        user.setProviderId("google");

                        return Mono.just(user);
                    }))
                    .flatMap(userRepository::save)
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

            })
            .defaultIfEmpty(ResponseEntity.notFound().build());

    }

}
