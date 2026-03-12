package nl.appsource.cardsever.api.controller;

import com.nimbusds.jose.JOSEException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converters.service.UserToOpenApiConverter;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardsever.api.config.CardServerJwtModem;
import nl.appsource.generated.openapi.model.LoginResponse;
import nl.appsource.generated.openapi.model.User;
import org.openapitools.api.LoadUserApi;
import org.openapitools.api.RotateJwtApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthController extends AbstractBaseController implements LoadUserApi, RotateJwtApi, V1Api {

    private final CardServerJwtModem cardServerJwtModem;
    private final UserToOpenApiConverter userToOpenApiConverter;
    private final UserRepository userRepository;

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

    @Override
    public Mono<ResponseEntity<LoginResponse>> rotateJwt(final ServerWebExchange exchange) {
        return getUserId(exchange)
            .doOnNext((userId) -> log.info("{} rotateJwt() userId={}", exchange.getRequest().getRemoteAddress(), userId))
            .flatMap(userRepository::findById)
            .map(userToOpenApiConverter::convert)
            .flatMap(user -> {
                try {
                    return Mono.just(new LoginResponse()
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
