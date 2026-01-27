package nl.appsource.cardserver.controller;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.UserToOpenApiConverter;
import nl.appsource.cardserver.model.SseSession;
import nl.appsource.cardserver.repository.SseSessionRepository;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.SseEventSender;
import nl.appsource.cardserver.service.UserService;
import org.openapitools.api.UsersApi;
import org.openapitools.model.CreateInvite;
import org.openapitools.model.CreateInviteResponse;
import org.openapitools.model.InvitesResponse;
import org.openapitools.model.PostMessage;
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
import java.util.Optional;

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
    public Mono<ResponseEntity<User>> getUser(final String userIdParam, final Optional<String> appIdentifier, final ServerWebExchange exchange) {
        log.info("{} getUser({}) appIdentifier={}", exchange.getRequest().getRemoteAddress(), userIdParam, appIdentifier);
        return authorize(appIdentifier, exchange)
            .flatMap(auth -> userService.findById(userIdParam)
                .mapNotNull(userToOpenApiConverter::convert)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<InvitesResponse>> getInvites(final Optional<String> appIdentifier, final ServerWebExchange exchange) {
        log.info("{} getInvites() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
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
    public Mono<@NonNull ResponseEntity<@NonNull Void>> ping(final Optional<String> appIdentifier, final ServerWebExchange exchange) {
//        log.info("{} ping() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
            .filter(ca -> ca.appIdentifier().isPresent())
            .flatMap(cardServerAuthentication -> sseSessionRepository.findByIdAndCreator(cardServerAuthentication.appIdentifier().orElseThrow(), cardServerAuthentication.user().getId()))
            .doOnNext(SseSession::ping)
            .flatMap(sseSessionRepository::save)
            .retryWhen(Retry.backoff(10, Duration.ofMillis(100)) // 3 attempts, exponential backoff
                .filter(this::isOptimisticLockingError)
                .doBeforeRetry(signal -> log.warn("Retry saving session, retry: " + signal.totalRetries()))
            )
            .delayUntil(sseSession -> sseEventSender.sendPong(sseSession.getId()))
            .map(_ -> ResponseEntity.ok().<Void>build())
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> pong(final Optional<String> appIdentifier, final ServerWebExchange exchange) {
//        log.info("{} pong() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
            .filter(ca -> ca.appIdentifier().isPresent())
            .flatMap(cardServerAuthentication -> sseSessionRepository.findByIdAndCreator(cardServerAuthentication.appIdentifier().orElseThrow(), cardServerAuthentication.user().getId()))
            .doOnNext(SseSession::pong)
            .flatMap(sseSessionRepository::save)
            .retryWhen(Retry.backoff(5, Duration.ofMillis(100)) // 3 attempts, exponential backoff
                .filter(this::isOptimisticLockingError)
                .doBeforeRetry(signal -> log.warn("Retry saving session, retry: " + signal.totalRetries()))
            )
            .map(_ -> ResponseEntity.ok().<Void>build())
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    private boolean isOptimisticLockingError(final Throwable ex) {
        // Spring Data maps the Couchbase CAS mismatch to OptimisticLockingFailureException
        return ex instanceof OptimisticLockingFailureException;
    }

    @Override
    public Mono<ResponseEntity<Flux<User>>> getUserByIds(final List<String> userIds, final Optional<String> appIdentifier, final ServerWebExchange exchange) {
        log.info("{} getUserByIds() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
            .map((_user) -> ResponseEntity.ok(userService.getUsers(userIds).mapNotNull(userToOpenApiConverter::convert)))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> removeInvite(final String friendId, final Optional<String> appIdentifier, final ServerWebExchange exchange) {
        log.info("{} removeInvites() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
            .flatMap(auth -> userService.removeInvite(auth.user().getId(), friendId).then(just(ResponseEntity.ok().<Void>build())))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());

    }

    @Override
    public Mono<ResponseEntity<Void>> acceptInvite(final String friendId, final Optional<String> appIdentifier, final ServerWebExchange exchange) {
        log.info("{} acceptInvite() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
            .flatMap(auth -> userService.acceptInvite(auth.user().getId(), friendId).then(just(ResponseEntity.ok().<Void>build())))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<CreateInviteResponse>> createInvite(final Mono<CreateInvite> arg, final Optional<String> appIdentifier, final ServerWebExchange exchange) {
        log.info("{} createInvite() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
            .flatMap(auth -> arg.flatMap(createInvite -> userService.createInvite(auth.user().getId(), createInvite.getSearchString()))
                .map(BigDecimal::new)
                .map(count -> new CreateInviteResponse().count(count))
                .map(ResponseEntity::ok)
                .switchIfEmpty(just(ResponseEntity.notFound()
                    .build())))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }


    @Override
    public Mono<ResponseEntity<User>> updatePreferences(final Mono<UpdatePreferences> arg, final Optional<String> appIdentifier, final ServerWebExchange exchange) {
        log.info("{} updatePreferences() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
            .flatMap(auth -> arg.flatMap(updatePreferences -> userService.updatePreferences(auth.user().getId(), updatePreferences))
                .mapNotNull(userToOpenApiConverter::convert)
                .map(ResponseEntity::ok)
                .switchIfEmpty(just(ResponseEntity.notFound().build())))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> usersMessage(final Mono<PostMessage> postMessageMono, final Optional<String> appIdentifier, final ServerWebExchange exchange) {
        log.info("{} usersMessage() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
            .delayUntil(auth -> postMessageMono.flatMap(postMessage -> userService.usersMessage(auth.user().getId(), postMessage.getRecipients(), postMessage.getMessage())))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.<Void>status(HttpStatus.UNAUTHORIZED).build());
    }

}
