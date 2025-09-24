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
import java.util.UUID;

import static reactor.core.publisher.Mono.just;

@RestController
@RequiredArgsConstructor
@Slf4j
public class UserController implements UsersApi, V1Api {

    private final UserService userService;

    private final SseEmitterRepository sseEmitterRepository;

    private final UserToOpenApiConverter userToOpenApiConverter;

    private Mono<String> getUserId(final ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("{} {} no authentication", exchange.getRequest()
                    .getRemoteAddress(), exchange.getRequest()
                    .getPath());
                return Mono.empty();
            }));
    }

    private Mono<String> authorize(final UUID appIdentifier, final ServerWebExchange exchange) {
        return getUserId(exchange)
            .filter((userId) -> sseEmitterRepository.validate(appIdentifier, userId))
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("{} {} sseEmitterRepository validation failed", exchange.getRequest()
                    .getRemoteAddress(), exchange.getRequest()
                    .getPath());
                return Mono.empty();
            }));
    }
    // FIXME: unauthorized

    @Override
    public Mono<ResponseEntity<User>> getUser(final UUID appIdentifier, final String userIdParam, final ServerWebExchange exchange) {

        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} getUser({})  userId={}", exchange.getRequest()
                .getRemoteAddress(), userIdParam, userId))
            .flatMap((userId) -> userService.findById(userIdParam))
            .mapNotNull(userToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound()
                .build());
    }

    @Override
    public Mono<ResponseEntity<InvitesResponse>> getInvites(final UUID appIdentifier, final ServerWebExchange exchange) {
//        log.info("{} getIncomingFriends()", exchange.getRequest().getRemoteAddress());
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} getInvites()  userId={}", exchange.getRequest()
                .getRemoteAddress(), userId))
            .flatMap(userService::getInvites)
            .flatMap(invites -> {

                final Flux<String> incoming = invites.incoming();
                final Flux<String> outgoing = invites.outgoing();
                final Flux<String> friends = invites.friends();

                return Mono.zip(arr -> new InvitesResponse().incoming((List<String>) arr[0])
                    .friends((List<String>) arr[1])
                    .outgoing((List<String>) arr[2]), incoming.collectList(), friends.collectList(), outgoing.collectList());

            })
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound()
                .build());
    }

    @Override
    public Mono<ResponseEntity<Void>> sendMessage(final UUID appIdentifier, final Mono<PostMessage> arg, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} sendMessage()  userId={}", exchange.getRequest()
                .getRemoteAddress(), userId))
            .flatMap(userId -> arg.map(postMessage -> {
                sseEmitterRepository.broadCastMessage(userId, postMessage.getMessage());
                return ResponseEntity.ok()
                    .<Void>build();
            }))
            .switchIfEmpty(just(ResponseEntity.notFound()
                .build()));
    }

    @Override
    public Mono<ResponseEntity<Void>> ping(final UUID appIdentifier, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> sseEmitterRepository.ping(appIdentifier))
            .then(just(ResponseEntity.ok()
                .<Void>build()))
            .switchIfEmpty(just(ResponseEntity.notFound()
                .build()));
    }

    @Override
    public Mono<ResponseEntity<Void>> pong(final UUID appIdentifier, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange).doOnNext((userId) -> sseEmitterRepository.pong(appIdentifier))
            .then(just(ResponseEntity.ok()
                .<Void>build()))
            .switchIfEmpty(just(ResponseEntity.notFound()
                .build()));
    }

    @Override
    public Mono<ResponseEntity<Flux<User>>> getUsers(final UUID appIdentifier, final List<String> userIds, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} getUsers()  userId={}", exchange.getRequest()
                .getRemoteAddress(), userId))
            .map((_userId) -> ResponseEntity.ok(userService.getUsers(userIds)
                .mapNotNull(userToOpenApiConverter::convert)))
            .switchIfEmpty(just(ResponseEntity.notFound()
                .build()));
    }

    @Override
    public Mono<ResponseEntity<Void>> removeInvite(final UUID appIdentifier, final String friendId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} removeInvites()  userId={}", exchange.getRequest()
                .getRemoteAddress(), userId))
            .flatMap(userId -> userService.removeInvite(userId, friendId))
            .then(just(ResponseEntity.ok()
                .<Void>build()))
            .switchIfEmpty(just(ResponseEntity.notFound()
                .build()));

    }

    @Override
    public Mono<ResponseEntity<Void>> acceptInvite(final UUID appIdentifier, final String friendId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} acceptInvite()  userId={}", exchange.getRequest()
                .getRemoteAddress(), userId))
            .flatMap(userId -> userService.acceptInvite(userId, friendId))
            .then(just(ResponseEntity.ok()
                .<Void>build()))
            .switchIfEmpty(just(ResponseEntity.notFound()
                .build()));
    }

    @Override
    public Mono<ResponseEntity<CreateInviteResponse>> createInvite(final UUID appIdentifier, final Mono<CreateInvite> arg, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} createInvite()  userId={}", exchange.getRequest()
                .getRemoteAddress(), userId))
            .flatMap(userId -> arg.flatMap(createInvite -> userService.createInvite(userId, createInvite.getSearchString())))
            .map(BigDecimal::new)
            .map(count -> new CreateInviteResponse().count(count))
            .map(ResponseEntity::ok)
            .switchIfEmpty(just(ResponseEntity.notFound()
                .build()));
    }


    @Override
    public Mono<ResponseEntity<User>> updatePreferences(final UUID appIdentifier, final Mono<UpdatePreferences> arg, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((userId) -> log.info("{} updatePreferences()  userId={}", exchange.getRequest().getRemoteAddress(), userId))
            .flatMap(userId -> arg.flatMap(updatePreferences -> userService.updatePreferences(userId, updatePreferences)))
            .mapNotNull(userToOpenApiConverter::convert)
            .map(ResponseEntity::ok)
            .switchIfEmpty(just(ResponseEntity.notFound()
                .build()));

    }
}
