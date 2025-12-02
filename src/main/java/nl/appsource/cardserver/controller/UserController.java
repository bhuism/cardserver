package nl.appsource.cardserver.controller;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.UserToOpenApiConverter;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.SseEmitterRepository;
import nl.appsource.cardserver.service.UserService;
import org.openapitools.api.UsersApi;
import org.openapitools.model.CreateInvite;
import org.openapitools.model.CreateInviteResponse;
import org.openapitools.model.InvitesResponse;
import org.openapitools.model.UpdatePreferences;
import org.openapitools.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static reactor.core.publisher.Mono.just;

@Slf4j
@RestController
public class UserController extends GenericController implements UsersApi, V1Api {

    private final UserService userService;

    private final UserToOpenApiConverter userToOpenApiConverter;

    public UserController(final SseEmitterRepository sseEmitterRepository, final UserRepository userRepositoryArg, final UserService userServiceArg, final UserToOpenApiConverter userToOpenApiConverterArg) {
        super(sseEmitterRepository, userRepositoryArg);
        this.userService = userServiceArg;
        this.userToOpenApiConverter = userToOpenApiConverterArg;
    }

    @Override
    public Mono<ResponseEntity<User>> getUser(final UUID appIdentifier, final String userIdParam, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} getUser({}) userId={}", exchange.getRequest().getRemoteAddress(), userIdParam, user.getId()))
            .flatMap((user) -> userService.findById(userIdParam)
                .mapNotNull(userToOpenApiConverter::convert)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<InvitesResponse>> getInvites(final UUID appIdentifier, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} getInvites() userId={}", exchange.getRequest().getRemoteAddress(), user.getId()))
            .flatMap(user -> userService.getInvites(user.getId()).flatMap(invites -> {
                        final Flux<String> incoming = invites.incoming();
                        final Flux<String> outgoing = invites.outgoing();
                        final Flux<String> friends = invites.friends();

                        return Mono.zip(arr -> new InvitesResponse().incoming((List<String>) arr[0])
                            .friends((List<String>) arr[1])
                            .outgoing((List<String>) arr[2]), incoming.collectList(), friends.collectList(), outgoing.collectList());
                    })
                    .map(ResponseEntity::ok)
                    .defaultIfEmpty(ResponseEntity.notFound().build())
            )
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<@NonNull  ResponseEntity<@NonNull Void>> ping(final UUID appIdentifier, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> sseEmitterRepository.ping(appIdentifier))
            .then(just(ResponseEntity.ok().<Void>build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> pong(final UUID appIdentifier, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> sseEmitterRepository.pong(appIdentifier))
            .then(just(ResponseEntity.ok().<Void>build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Flux<User>>> getUsers(final UUID appIdentifier, final List<String> userIds, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} getUsers() user.getId()={}", exchange.getRequest().getRemoteAddress(), user.getId()))
            .map((_user) -> ResponseEntity.ok(userService.getUsers(userIds).mapNotNull(userToOpenApiConverter::convert)))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> removeInvite(final UUID appIdentifier, final String friendId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} removeInvites() user.getId()={}", exchange.getRequest().getRemoteAddress(), user.getId()))
            .flatMap(user -> userService.removeInvite(user.getId(), friendId).then(just(ResponseEntity.ok().<Void>build())))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());

    }

    @Override
    public Mono<ResponseEntity<Void>> acceptInvite(final UUID appIdentifier, final String friendId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} acceptInvite() userId={}", exchange.getRequest()
                .getRemoteAddress(), user.getId()))
            .flatMap(user -> userService.acceptInvite(user.getId(), friendId).then(just(ResponseEntity.ok().<Void>build())))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<CreateInviteResponse>> createInvite(final UUID appIdentifier, final Mono<CreateInvite> arg, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} createInvite() userId={}", exchange.getRequest()
                .getRemoteAddress(), user.getId()))
            .flatMap(user -> arg.flatMap(createInvite -> userService.createInvite(user.getId(), createInvite.getSearchString()))
                .map(BigDecimal::new)
                .map(count -> new CreateInviteResponse().count(count))
                .map(ResponseEntity::ok)
                .switchIfEmpty(just(ResponseEntity.notFound()
                    .build())))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }


    @Override
    public Mono<ResponseEntity<User>> updatePreferences(final UUID appIdentifier, final Mono<UpdatePreferences> arg, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} updatePreferences() userId={}", exchange.getRequest().getRemoteAddress(), user.getId()))
            .flatMap(user -> arg.flatMap(updatePreferences -> userService.updatePreferences(user.getId(), updatePreferences))
                .mapNotNull(userToOpenApiConverter::convert)
                .map(ResponseEntity::ok)
                .switchIfEmpty(just(ResponseEntity.notFound()
                    .build())))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> reloadUser(final UUID appIdentifier, final String gameId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} reloadUser() userId={} gameId={}", exchange.getRequest()
                .getRemoteAddress(), user.getId(), gameId))
            .flatMap(user -> userService.reload(appIdentifier, user.getId(), gameId)
                .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok()
                    .build()))
                .defaultIfEmpty(ResponseEntity.notFound()
                    .build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

}
