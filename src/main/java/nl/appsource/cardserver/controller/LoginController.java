package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.UserRepository;
import org.openapitools.api.LoginApi;
import org.openapitools.model.LoginRequest;
import org.openapitools.model.LoginResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class LoginController implements LoginApi {

    private final UserRepository userRepository;

    @Override
    public ResponseEntity<LoginResponse> login(final LoginRequest loginRequest) {

        final String email = loginRequest.getEmail();

        log.info("login: {}", email);

        return Optional.of(userRepository.findByEmail(email).orElseGet(() -> {
                final User userNew = new User();

                userNew.setLastLogin(Instant.now());
                userNew.setEmail(loginRequest.getEmail());
                userNew.setName(loginRequest.getName());
                userNew.setCreated(Instant.now());
                userNew.setUpdated(Instant.now());
                userNew.setDisplayName(loginRequest.getDisplayName());
                userNew.setPhotoURL(loginRequest.getPhotoURL());
                userNew.setProviderId(loginRequest.getProviderId());

                log.info("Created new user: {}", userNew);

                return userNew;
            }))
            .map(userRepository::save)

            .map(user -> {
                final LoginResponse loginResponse = new LoginResponse();
                loginResponse.setUid(user.getId());
                return loginResponse;
            }).map(ResponseEntity::ok).get();


    }
}
