package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.UserToOpenApiConverter;
import nl.appsource.cardserver.service.SseEmitterRepository;
import nl.appsource.cardserver.service.UserService;
import org.openapitools.api.UsersApi;
import org.openapitools.model.CreateInvite;
import org.openapitools.model.CreateInviteResponse;
import org.openapitools.model.InvitesResponse;
import org.openapitools.model.Ping;
import org.openapitools.model.Pong;
import org.openapitools.model.PostMessage;
import org.openapitools.model.UpdatePreferences;
import org.openapitools.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static reactor.core.publisher.Mono.just;

@RestController
@RequiredArgsConstructor
@Slf4j
public class UserController implements UsersApi, V1Api {

    private final UserService userService;

    private final SseEmitterRepository sseEmitterRepository;

    private final UserToOpenApiConverter userToOpenApiConverter;

    @Override
    public Mono<ResponseEntity<User>> getUser(final String userId, final ServerWebExchange exchange) {

        log.info("{} getUser({})", exchange.getRequest().getRemoteAddress(), userId);

        return userService.findById(userId).mapNotNull(userToOpenApiConverter::convert).map(ResponseEntity::ok);
    }


    @Override
    public Mono<ResponseEntity<InvitesResponse>> getInvites(final ServerWebExchange exchange) {

        log.info("{} getIncomingFriends()", exchange.getRequest().getRemoteAddress());

        return ReactiveSecurityContextHolder.getContext().map(SecurityContext::getAuthentication).map(Authentication::getName).flatMap(userService::getInvites).flatMap(invites -> {

//                final InvitesResponse invitesResponse = new InvitesResponse();

            final Flux<String> incoming = invites.incoming();
            final Flux<String> outgoing = invites.outgoing();
            final Flux<String> friends = invites.friends();

            return Mono.zip(arr -> new InvitesResponse().incoming((List<String>) arr[0]).friends((List<String>) arr[1]).outgoing((List<String>) arr[2]), incoming.collectList(), friends.collectList(), outgoing.collectList());

        }).map(ResponseEntity::ok);

    }

    @Override
    public Mono<ResponseEntity<Void>> sendMessage(final Mono<PostMessage> arg, final ServerWebExchange exchange) {

        log.info("{} sendMessage()", exchange.getRequest().getRemoteAddress());

        return ReactiveSecurityContextHolder.getContext().map(SecurityContext::getAuthentication).map(Authentication::getName).flatMap(userId -> arg.map(postMessage -> {
            sseEmitterRepository.broadCastMessage(userId, postMessage.getMessage());
            return ResponseEntity.ok().build();
        }));
    }

    @Override
    public Mono<ResponseEntity<Void>> ping(final Mono<Ping> ping, final ServerWebExchange exchange) {
        return ping.map(data -> {
            sseEmitterRepository.ping(data.getUuid());
            return ResponseEntity.ok().build();
        });
    }

    @Override
    public Mono<ResponseEntity<Void>> pong(final Mono<Pong> pong, final ServerWebExchange exchange) {
        return pong.map(data -> {
            sseEmitterRepository.pong(data.getUuid());
            return ResponseEntity.ok().build();
        });
    }

    @Override
    public Mono<ResponseEntity<Flux<User>>> getUsers(final List<String> userIds, final ServerWebExchange exchange) {

        log.info("{} getUsers()", exchange.getRequest().getRemoteAddress());

        return just(ResponseEntity.ok(userService.getUsers(userIds).mapNotNull(userToOpenApiConverter::convert)));
    }

    @Override
    public Mono<ResponseEntity<Void>> removeInvite(final String friendId, final ServerWebExchange exchange) {

        log.info("{} removeInvite({})", exchange.getRequest().getRemoteAddress(), friendId);

        return ReactiveSecurityContextHolder.getContext().map(SecurityContext::getAuthentication).map(Authentication::getName).flatMap(userId -> userService.removeInvite(userId, friendId)).then(just(ResponseEntity.ok().build()));

    }

    @Override
    public Mono<ResponseEntity<Void>> acceptInvite(final String friendId, final ServerWebExchange exchange) {
        log.info("{} addInvite({})", exchange.getRequest().getRemoteAddress(), friendId);
        return ReactiveSecurityContextHolder.getContext().map(SecurityContext::getAuthentication).map(Authentication::getName).flatMap(userId -> userService.acceptInvite(userId, friendId)).then(just(ResponseEntity.ok().build()));
    }

    @Override
    public Mono<ResponseEntity<CreateInviteResponse>> createInvite(final Mono<CreateInvite> arg, final ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext().map(SecurityContext::getAuthentication).map(Authentication::getName).flatMap(userId -> arg.flatMap(createInvite -> userService.createInvite(userId, createInvite.getSearchString()))).map(BigDecimal::new).map(count -> new CreateInviteResponse().count(count)).map(ResponseEntity::ok);
    }


    @Override
    public Mono<ResponseEntity<User>> updatePreferences(final Mono<UpdatePreferences> arg, final ServerWebExchange exchange) {
        log.info("{} updatePreferences()", exchange.getRequest().getRemoteAddress());
        return ReactiveSecurityContextHolder.getContext().map(SecurityContext::getAuthentication).map(Authentication::getName).flatMap(userId -> arg.flatMap(updatePreferences -> userService.updateName(userId, updatePreferences.getDisplayName()))).mapNotNull(userToOpenApiConverter::convert).map(ResponseEntity::ok);

    }
}
