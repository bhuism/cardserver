package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.UserToOpenApiConverter;
import nl.appsource.cardserver.filter.LoggingFilter;
import nl.appsource.cardserver.service.SseEmitterRepository;
import nl.appsource.cardserver.service.UserService;
import org.openapitools.api.UsersApi;
import org.openapitools.model.CreateInvite;
import org.openapitools.model.CreateInviteResponse;
import org.openapitools.model.InvitesResponse;
import org.openapitools.model.PostMessage;
import org.openapitools.model.UpdatePreferences;
import org.openapitools.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
public class UserController implements UsersApi, V1Api {

    private static final Comparator<? super User> ONLINEFIRST = (o1, o2) -> {
        if (o1.getOnline() && !o2.getOnline()) {
            return -1;
        }

        if (!o1.getOnline() && o2.getOnline()) {
            return 1;
        }

        return 0;

    };

    private final UserService userService;

    private final SseEmitterRepository sseEmitterRepository;

    private final UserToOpenApiConverter userToOpenApiConverter;

    @Override
    public ResponseEntity<org.openapitools.model.User> getUser(final String userId) {

        LoggingFilter.requestLogMessage("getUser(" + userId + ")");

        return userService.findById(userId).map(userToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }


    @Override
    public ResponseEntity<InvitesResponse> getInvites() {

        LoggingFilter.requestLogMessage("getIncomingFriends()");

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String userId = authentication.getName();

        return userService.getInvites(userId).map(invites -> {

            final InvitesResponse invitesResponse = new InvitesResponse();

            invitesResponse.setIncoming(invites.getIncoming().stream().map(userToOpenApiConverter::convert).collect(Collectors.toList()));
            invitesResponse.setOutgoing(invites.getOutgoing().stream().map(userToOpenApiConverter::convert).collect(Collectors.toList()));
            invitesResponse.setFriends(invites.getFriends().stream().map(userToOpenApiConverter::convert).sorted(ONLINEFIRST).collect(Collectors.toList()));

            LoggingFilter.requestLogMessage(("incoming: " + invites.getIncoming().size() + ", outgoing: " + invites.getOutgoing().size() + ", friends: " + invites.getFriends().size()));

            return invitesResponse;

        }).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());

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
    public ResponseEntity<Void> removeInvite(final String friendId) {
        LoggingFilter.requestLogMessage("removeInvite(" + friendId + ")");

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String userId = authentication.getName();

        userService.removeInvite(userId, friendId);

        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> acceptInvite(final String friendId) {
        LoggingFilter.requestLogMessage("addInvite(" + friendId + ")");

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String userId = authentication.getName();

        userService.acceptInvite(userId, friendId);

        return ResponseEntity.ok().build();

    }


    @Override
    public ResponseEntity<CreateInviteResponse> createInvite(final CreateInvite createInvite) {
        LoggingFilter.requestLogMessage("addInvite('" + createInvite.getSearchString() + "')");

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String userId = authentication.getName();

        return userService
            .createInvite(userId, createInvite.getSearchString())
            .map(invitees -> invitees.stream().map(userToOpenApiConverter::convert).collect(Collectors.toList()))
            .map(invitees -> new CreateInviteResponse().invitees(invitees))
            .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());

    }

    @Override
    public ResponseEntity<User> updatePreferences(final UpdatePreferences updatePreferences) {

        LoggingFilter.requestLogMessage("updatePreferences()");

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String userId = authentication.getName();

        return userService.updateName(userId, updatePreferences.getDisplayName())
            .map(userToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());

    }
}
