package nl.appsource.cardserver.controller;

import com.nimbusds.jose.JOSEException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.UserToOpenApiConverter;
import nl.appsource.cardserver.repository.SseSessionRepository;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.CardServerJwtModem;
import org.openapitools.api.LoadUserApi;
import org.openapitools.api.RotateJwtApi;
import org.openapitools.model.LoginResponse;
import org.openapitools.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@RestController
public class AuthController extends GenericController implements LoadUserApi, RotateJwtApi {

    private final CardServerJwtModem cardServerJwtModem;
    private final UserToOpenApiConverter userToOpenApiConverter;
    private final UserRepository userRepository;

    public AuthController(final UserRepository userRepository, final CardServerJwtModem cardServerJwtModem, final UserToOpenApiConverter userToOpenApiConverterArg, final SseSessionRepository sseSessionRepository) {
        super(userRepository, sseSessionRepository);
        this.cardServerJwtModem = cardServerJwtModem;
        this.userToOpenApiConverter = userToOpenApiConverterArg;
        this.userRepository = userRepository;
    }

    @Override
    public Mono<@NonNull ResponseEntity<@NonNull User>> loadUser(final ServerWebExchange exchange) {
        return getUserId(exchange)
            .doOnNext(user -> user.setLastLogin(Instant.now()))
            .flatMap(userRepository::save)
            .mapNotNull(userToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<LoginResponse>> rotateJwt(final ServerWebExchange exchange) {
        return getUserId(exchange)
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
