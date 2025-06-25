package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.UserToOpenApiConverter;
import nl.appsource.cardserver.filter.LoggingFilter;
import nl.appsource.cardserver.service.CardServerJwtModem;
import nl.appsource.cardserver.service.UserService;
import org.openapitools.api.WhoamiApi;
import org.openapitools.model.WhoAmIResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static nl.appsource.cardserver.service.GameServiceImpl.idGen;

@RestController
@RequiredArgsConstructor
@Slf4j
public class WhoAmIController implements WhoamiApi {

    private final UserService userService;

    private final CardServerJwtModem cardServerJwtModem;
    private final UserToOpenApiConverter userToOpenApiConverter;


    @Override
    public ResponseEntity<WhoAmIResponse> whoami() {

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        final Jwt principal = (Jwt) authentication.getPrincipal();
        final String email = principal.getClaims().get("email").toString();

        LoggingFilter.requestLogMessage(("whoampi(), email=" + email));

        final Instant now = Instant.now();

        return Optional.of(userService.findByEmail(email)
                .map((user) -> {

                    log.info("User login {}", user.getDisplayName());

                    user.setLastLogin(now);
                    user.setUpdated(now);
                    return user;
                })
                .orElseGet(() -> {

                    log.info("Creating a new user {}", email);

                    final nl.appsource.cardserver.model.User user = new nl.appsource.cardserver.model.User();

                    user.setId(idGen(28));
                    user.setEmail(email);
                    user.setCreated(now);
                    user.setUpdated(now);
                    user.setName(principal.getClaims().get("name").toString());
                    user.setDisplayName(principal.getClaims().get("name").toString());
                    user.setInvites(emptyList());
                    user.setLastLogin(now);
                    user.setPhotoURL(principal.getClaims().get("picture") != null ? principal.getClaims().get("picture").toString() : null);
                    user.setProviderId("google");

                    return user;
                }))
            .map(userService::save)
            .map(userToOpenApiConverter::convert)
            .map((user) -> new WhoAmIResponse().user(user).jwt(cardServerJwtModem.encode(user.getId()).serialize()))
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());

    }


}
