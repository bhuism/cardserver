package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.filter.LoggingFilter;
import nl.appsource.cardserver.service.UserService;
import org.openapitools.api.UsersApi;
import org.openapitools.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class UserController implements UsersApi {

    private final UserService userService;

    @Override
    public ResponseEntity<org.openapitools.model.User> getUser(final String userId) {

        LoggingFilter.requestLogMessage("getUser(" + userId + ")");

        return userService.findById(userId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<Set<User>> getIncomingFriends() {

        LoggingFilter.requestLogMessage("getIncomingFriends()");

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String email = "" + authentication.getPrincipal();

        return userService.findByEmail(email)
            .map(userService::findAllIncomingInvites)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

}
