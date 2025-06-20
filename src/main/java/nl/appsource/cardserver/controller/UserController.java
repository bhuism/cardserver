package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.UserToOpenApiConverter;
import nl.appsource.cardserver.filter.LoggingFilter;
import nl.appsource.cardserver.service.SseEmitterRepository;
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

    private final SseEmitterRepository sseEmitterRepository;

    private final UserToOpenApiConverter userToOpenApiConverter;

    @Override
    public ResponseEntity<org.openapitools.model.User> getUser(final String userId) {

        LoggingFilter.requestLogMessage("getUser(" + userId + ")");

        return userService.findById(userId).map(userToOpenApiConverter::convert).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<List<User>> getIncomingInvites() {

        LoggingFilter.requestLogMessage("getIncomingFriends()");

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String userId = authentication.getName();

        final List<User> users = userService.findIncomingInvites(userId).stream().map(userToOpenApiConverter::convert).collect(Collectors.toCollection(ArrayList::new));

        return ResponseEntity.ok(users);
    }

    @Override
    public ResponseEntity<Void> sendMessage(final PostMessage postMessage) {

        LoggingFilter.requestLogMessage("sendAMessage()");

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String userId = authentication.getName();

        sseEmitterRepository.sendMessage(userId, postMessage.getMessage());

        return ResponseEntity.ok().build();
    }


    @Override
    public ResponseEntity<UUID> ping(final UUID uuid) {
        sseEmitterRepository.ping(uuid);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<UUID> pong(final UUID uuid) {
        sseEmitterRepository.pong(uuid);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<List<User>> getUsers(final List<String> userIds) {

        LoggingFilter.requestLogMessage("getUsers()");

//        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//        final String userId = authentication.getName();

        return ResponseEntity.ok(userService.getUsers(userIds).stream().map(userToOpenApiConverter::convert).collect(Collectors.toList()));
    }

    @Override
    public ResponseEntity<User> removeInvite(final String friendId) {
        LoggingFilter.requestLogMessage("removeInvite(" + friendId + ")");

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String userId = authentication.getName();

        return userService.removeInvite(userId, friendId).map(userToOpenApiConverter::convert).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<User> addInvite(final String friendId) {
        LoggingFilter.requestLogMessage("addInvite(" + friendId + ")");

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String userId = authentication.getName();

        return userService.addInvite(userId, friendId).map(userToOpenApiConverter::convert).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
