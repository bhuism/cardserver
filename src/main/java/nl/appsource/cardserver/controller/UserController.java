package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.service.UserService;
import org.openapitools.api.UsersApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController implements UsersApi {

    private final UserService userService;

    @Override
    public ResponseEntity<org.openapitools.model.User> getUser(final String userId) {
        return userService.findById(userId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

}
