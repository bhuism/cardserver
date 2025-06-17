package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.UserToOpenApiConverter;
import nl.appsource.cardserver.filter.LoggingFilter;
import nl.appsource.cardserver.service.MessageEngine;
import nl.appsource.cardserver.service.UserService;
import org.openapitools.api.UsersApi;
import org.openapitools.model.PostMessage;
import org.openapitools.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
public class UserController implements UsersApi, V1Api {

    private final UserService userService;

    private final MessageEngine messageEngine;

    private final UserToOpenApiConverter userToOpenApiConverter;

    @Override
    public ResponseEntity<org.openapitools.model.User> getUser(final String userId) {

        LoggingFilter.requestLogMessage("getUser(" + userId + ")");

        return userService.findById(userId)
            .map(userToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<List<User>> getIncomingFriends() {

        LoggingFilter.requestLogMessage("getIncomingFriends()");

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String userId = authentication.getName();

        final List<User> users = userService.findAllIncomingInvites(userId).stream().map(userToOpenApiConverter::convert).collect(Collectors.toCollection(ArrayList::new));

        return ResponseEntity.ok(users);
    }

    @Override
    public ResponseEntity<Void> sendMessage(final PostMessage postMessage) {

        LoggingFilter.requestLogMessage("sendAMessage()");

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String userId = authentication.getName();

        messageEngine.message(userId, postMessage.getMessage());

        return ResponseEntity.ok().build();
    }


    @Override
    public ResponseEntity<UUID> ping(final UUID uuid) {
        LoggingFilter.requestLogMessage("ping");

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String userId = authentication.getName();

        messageEngine.ping(userId, uuid);

        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<UUID> pong(final UUID uuid) {
        LoggingFilter.requestLogMessage("pong");

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String userId = authentication.getName();

        messageEngine.pong(userId, uuid);

        return ResponseEntity.ok().build();
    }

}
