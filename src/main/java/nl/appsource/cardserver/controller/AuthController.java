package nl.appsource.cardserver.controller;

import com.nimbusds.jose.JOSEException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.UserToOpenApiConverter;
import nl.appsource.cardserver.repository.SseSessionRepository;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.CardServerJwtModem;
import nl.appsource.cardserver.service.UserService;
import org.openapitools.api.LoadUserApi;
import org.openapitools.api.RotateJwtApi;
import org.openapitools.model.LoginResponse;
import org.openapitools.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
public class AuthController extends GenericController implements LoadUserApi, RotateJwtApi, V1Api {

    private final CardServerJwtModem cardServerJwtModem;
    private final UserToOpenApiConverter userToOpenApiConverter;
    private final UserRepository userRepository;
    private final UserService userService;

    public AuthController(final UserRepository userRepository, final CardServerJwtModem cardServerJwtModem, final UserToOpenApiConverter userToOpenApiConverter, final SseSessionRepository sseSessionRepository, final UserService userService) {
        super(userRepository, sseSessionRepository, userService);
        this.cardServerJwtModem = cardServerJwtModem;
        this.userToOpenApiConverter = userToOpenApiConverter;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Override
    public Mono<@NonNull ResponseEntity<@NonNull User>> loadUser(final ServerWebExchange exchange) {
        return getUserId(exchange)
            .doOnNext((userId) -> log.info("{} loadUser() userId={}", exchange.getRequest().getRemoteAddress(), userId))
            .flatMap(userRepository::updateLastLogin)
            .map(userToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<LoginResponse>> rotateJwt(final ServerWebExchange exchange) {
        return getUserId(exchange)
            .doOnNext((userId) -> log.info("{} rotateJwt() userId={}", exchange.getRequest().getRemoteAddress(), userId))
            .flatMap(userRepository::findById)
            .map(userToOpenApiConverter::convert)
            .flatMap(user -> {
                try {
                    return Mono.just(
                        new LoginResponse()
                            .user(user)
                            .jwt(cardServerJwtModem.encode(user.getId()).serialize())
                    );
                } catch (JOSEException e) {
                    return Mono.error(e);
                }
            })
            .map(ResponseEntity::ok);
    }
}
