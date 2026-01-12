package nl.appsource.cardserver.controller;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.UserToOpenApiConverter;
import nl.appsource.cardserver.model.SseSession;
import nl.appsource.cardserver.repository.SseSessionRepository;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.SseEventSender;
import nl.appsource.cardserver.service.UserService;
import nl.appsource.cardserver.utils.CardServerAuthentication;
import org.openapitools.api.UsersApi;
import org.openapitools.model.CreateInvite;
import org.openapitools.model.CreateInviteResponse;
import org.openapitools.model.InvitesResponse;
import org.openapitools.model.UpdatePreferences;
import org.openapitools.model.User;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static reactor.core.publisher.Mono.just;

@Slf4j
@RestController
public class UserController extends GenericController implements UsersApi, V1Api {

    private final UserService userService;

    private final UserToOpenApiConverter userToOpenApiConverter;

    private final SseSessionRepository sseSessionRepository;

    private final SseEventSender sseEventSender;

    public UserController(final SseSessionRepository sseSessionRepository, final UserRepository userRepositoryArg, final UserService userServiceArg, final UserToOpenApiConverter userToOpenApiConverterArg, final SseEventSender sseEventSender) {
        super(userRepositoryArg, sseSessionRepository);
        this.userService = userServiceArg;
        this.userToOpenApiConverter = userToOpenApiConverterArg;
        this.sseSessionRepository = sseSessionRepository;
        this.sseEventSender = sseEventSender;
    }

    @Override
    public Mono<ResponseEntity<User>> getUser(final String appIdentifier, final String userIdParam, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> log.info("{} getUser({}) userId={}", exchange.getRequest().getRemoteAddress(), userIdParam, auth.user().getId()))
            .flatMap(auth -> userService.findById(userIdParam)
                .mapNotNull(userToOpenApiConverter::convert)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<InvitesResponse>> getInvites(final String appIdentifier, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> log.info("{} getInvites() userId={}", exchange.getRequest().getRemoteAddress(), auth.user().getId()))
            .flatMap(auth -> userService.getInvites(auth.user().getId()).flatMap(invites -> {
                        final Flux<String> incoming = invites.incoming();
                        final Flux<String> outgoing = invites.outgoing();
                        final Flux<String> friends = invites.friends();
                        return Mono.zip(arr -> new InvitesResponse()
                            .incoming((List<String>) arr[0])
                            .friends((List<String>) arr[1])
                            .outgoing((List<String>) arr[2]), incoming.collectList(), friends.collectList(), outgoing.collectList());
                    })
                    .map(ResponseEntity::ok)
                    .defaultIfEmpty(ResponseEntity.notFound().build())
            )
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<@NonNull  ResponseEntity<@NonNull Void>> ping(final String appIdentifier, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            //.doOnNext(auth -> log.info("{} ping() userId={}", exchange.getRequest().getRemoteAddress(), auth.user().getId()))
            .map(CardServerAuthentication::sseSession)
            .map(SseSession::getId)
            .flatMap(sseSessionRepository::findById)
            .doOnNext(SseSession::ping)
            .flatMap(sseSessionRepository::save)
            .retryWhen(Retry.backoff(10, Duration.ofMillis(100)) // 3 attempts, exponential backoff
                .filter(this::isOptimisticLockingError)
                .doBeforeRetry(signal -> log.warn("Retry saving session, retry: " + signal.totalRetries()))
            )
            .flatMap(sseSession -> sseEventSender.sendPong(sseSession.getId()))
            .then(just(ResponseEntity.ok().<Void>build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    private boolean isOptimisticLockingError(final Throwable ex) {
        // Spring Data maps the Couchbase CAS mismatch to OptimisticLockingFailureException
        return ex instanceof OptimisticLockingFailureException;
    }

    @Override
    public Mono<ResponseEntity<Void>> pong(final String appIdentifier, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            //.doOnNext(auth -> log.info("{} pong() userId={}", exchange.getRequest().getRemoteAddress(), auth.user().getId()))
            .map(CardServerAuthentication::sseSession)
            .map(SseSession::getId)
            .flatMap(sseSessionRepository::findById)
            .doOnNext(SseSession::pong)
            .flatMap(sseSessionRepository::save)
            .retryWhen(Retry.backoff(5, Duration.ofMillis(50)) // 3 attempts, exponential backoff
                .filter(this::isOptimisticLockingError)
                .doBeforeRetry(signal -> log.warn("Retry saving session, retry: " + signal.totalRetries()))
            )
            .then(just(ResponseEntity.ok().<Void>build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Flux<User>>> getUserByIds(final String appIdentifier, final List<String> userIds, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> log.info("{} getUserByIds() userId={}", exchange.getRequest().getRemoteAddress(), auth.user().getId()))
            .map((_user) -> ResponseEntity.ok(userService.getUsers(userIds).mapNotNull(userToOpenApiConverter::convert)))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> removeInvite(final String appIdentifier, final String friendId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> log.info("{} removeInvites() userId={}", exchange.getRequest().getRemoteAddress(), auth.user().getId()))
            .flatMap(auth -> userService.removeInvite(auth.user().getId(), friendId).then(just(ResponseEntity.ok().<Void>build())))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());

    }

    @Override
    public Mono<ResponseEntity<Void>> acceptInvite(final String appIdentifier, final String friendId, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> log.info("{} acceptInvite() userId={}", exchange.getRequest()
                .getRemoteAddress(), auth.user().getId()))
            .flatMap(auth -> userService.acceptInvite(auth.user().getId(), friendId).then(just(ResponseEntity.ok().<Void>build())))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<CreateInviteResponse>> createInvite(final String appIdentifier, final Mono<CreateInvite> arg, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> log.info("{} createInvite() userId={}", exchange.getRequest()
                .getRemoteAddress(), auth.user().getId()))
            .flatMap(auth -> arg.flatMap(createInvite -> userService.createInvite(auth.user().getId(), createInvite.getSearchString()))
                .map(BigDecimal::new)
                .map(count -> new CreateInviteResponse().count(count))
                .map(ResponseEntity::ok)
                .switchIfEmpty(just(ResponseEntity.notFound()
                    .build())))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }


    @Override
    public Mono<ResponseEntity<User>> updatePreferences(final String appIdentifier, final Mono<UpdatePreferences> arg, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext(auth -> log.info("{} updatePreferences() userId={}", exchange.getRequest().getRemoteAddress(), auth.user().getId()))
            .flatMap(auth -> arg.flatMap(updatePreferences -> userService.updatePreferences(auth.user().getId(), updatePreferences))
                .mapNotNull(userToOpenApiConverter::convert)
                .map(ResponseEntity::ok)
                .switchIfEmpty(just(ResponseEntity.notFound().build())))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

}
