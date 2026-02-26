package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.UserToOpenApiConverter;
import nl.appsource.cardserver.service.UserService;
import nl.appsource.generated.openapi.model.CreateInvite;
import nl.appsource.generated.openapi.model.CreateInviteResponse;
import nl.appsource.generated.openapi.model.InvitesResponse;
import nl.appsource.generated.openapi.model.PostMessage;
import nl.appsource.generated.openapi.model.UpdatePreferences;
import nl.appsource.generated.openapi.model.User;
import org.openapitools.api.UsersApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static reactor.core.publisher.Mono.just;

@Slf4j
@RestController
@RequiredArgsConstructor
public class UserController extends AbstractBaseController implements UsersApi, V1Api {

    private final UserService userService;

    private final UserToOpenApiConverter userToOpenApiConverter;

    @Override
    public Mono<ResponseEntity<User>> getUser(final String appIdentifier, final String userIdParam, final ServerWebExchange exchange) {
        log.info("{} getUser({}) appIdentifier={}", exchange.getRequest().getRemoteAddress(), userIdParam, appIdentifier);
        return authorize(appIdentifier, exchange)
            .flatMap(auth -> userService.findById(userIdParam)
                .mapNotNull(userToOpenApiConverter::convert)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<InvitesResponse>> getInvites(final String appIdentifier, final ServerWebExchange exchange) {
        log.info("{} getInvites() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
            .flatMap(auth -> userService.getInvites(auth.userId()).flatMap(invites -> {
                        final Flux<String> incoming = invites.incoming();
                        final Flux<String> outgoing = invites.outgoing();
                        final Flux<String> friends = invites.friends();
                        return Mono.zip(arr -> InvitesResponse.builder()
                            .incoming((List<String>) arr[0])
                            .friends((List<String>) arr[1])
                            .outgoing((List<String>) arr[2]).build(), incoming.collectList(), friends.collectList(), outgoing.collectList());
                    })
                    .map(ResponseEntity::ok)
                    .defaultIfEmpty(ResponseEntity.notFound().build())
            )
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Flux<User>>> getUserByIds(final String appIdentifier, final Set<String> userIds, final ServerWebExchange exchange) {
        log.info("{} getUserByIds() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
            .map((_user) -> ResponseEntity.ok(userService.getUsers(Set.copyOf(userIds)).mapNotNull(userToOpenApiConverter::convert)))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> removeInvite(final String appIdentifier, final String friendId, final ServerWebExchange exchange) {
        log.info("{} removeInvites() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
            .flatMap(auth -> userService.removeInvite(auth.userId(), friendId).then(just(ResponseEntity.ok().<Void>build())))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());

    }

    @Override
    public Mono<ResponseEntity<Void>> acceptInvite(final String appIdentifier, final String friendId, final ServerWebExchange exchange) {
        log.info("{} acceptInvite() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
            .flatMap(auth -> userService.acceptInvite(auth.userId(), friendId).then(just(ResponseEntity.ok().<Void>build())))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<CreateInviteResponse>> createInvite(final String appIdentifier, final Mono<CreateInvite> createInviteMono, final ServerWebExchange exchange) {
        log.info("{} createInvite() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
            .flatMap(auth -> createInviteMono.flatMap(createInvite -> userService.createInvite(auth.userId(), createInvite.getSearchString()))
                .map(BigDecimal::new)
                .map(count -> CreateInviteResponse.builder().count(count).build())
                .map(ResponseEntity::ok)
                .switchIfEmpty(just(ResponseEntity.notFound()
                    .build())))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }


    @Override
    public Mono<ResponseEntity<User>> updatePreferences(final String appIdentifier, final Mono<UpdatePreferences> updatePreferencesMono, final ServerWebExchange exchange) {
        log.info("{} updatePreferences() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
            .flatMap(auth -> updatePreferencesMono.flatMap(updatePreferences -> userService.updatePreferences(auth.userId(), updatePreferences))
                .mapNotNull(userToOpenApiConverter::convert)
                .map(ResponseEntity::ok)
                .switchIfEmpty(just(ResponseEntity.notFound().build())))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> usersMessage(final String appIdentifier, final Mono<PostMessage> postMessageMono, final ServerWebExchange exchange) {
        log.info("{} usersMessage() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
            .delayUntil(auth -> postMessageMono.flatMap(postMessage -> userService.usersMessage(auth.userId(), Set.copyOf(postMessage.getRecipients()), postMessage.getMessage())))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.<Void>status(HttpStatus.UNAUTHORIZED).build());
    }

}
