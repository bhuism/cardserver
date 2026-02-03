package nl.appsource.cardserver.controller;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.UserToOpenApiConverter;
import nl.appsource.cardserver.repository.SseSessionRepository;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.SseEventSender;
import nl.appsource.cardserver.service.UserService;
import nl.appsource.cardserver.utils.CardServerAuthentication;
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

import java.math.BigDecimal;
import java.util.List;

import static reactor.core.publisher.Mono.just;

@Slf4j
@RestController
public class UserController extends GenericController implements UsersApi, V1Api {

    private final UserService userService;

    private final UserToOpenApiConverter userToOpenApiConverter;

    private final SseSessionRepository sseSessionRepository;

    private final SseEventSender sseEventSender;

    public UserController(final SseSessionRepository sseSessionRepository, final UserRepository userRepository, final UserService userService, final UserToOpenApiConverter userToOpenApiConverter, final SseEventSender sseEventSender) {
        super(userRepository, sseSessionRepository, userService);
        this.userService = userService;
        this.userToOpenApiConverter = userToOpenApiConverter;
        this.sseSessionRepository = sseSessionRepository;
        this.sseEventSender = sseEventSender;
    }

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
    public Mono<@NonNull ResponseEntity<@NonNull Void>> ping(final String appIdentifier, final ServerWebExchange exchange) {
//        log.info("{} ping() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
//            .filter(ca -> ca.appIdentifier().isPresent())
            //          .flatMap(auth -> sseSessionRepository.findByIdAndCreator(auth.appIdentifier(), auth.userId()))
//            .doOnNext(SseSession::ping)

            .map(CardServerAuthentication::appIdentifier)
            .flatMap(sseSessionRepository::ping)
//            .retryWhen(Retry.backoff(5, Duration.ofMillis(100)) // 3 attempts, exponential backoff
//                .filter(this::isOptimisticLockingError)
//                .doBeforeRetry(signal -> log.warn("Retry saving session, retry: " + signal.totalRetries()))
//            )
            .delayUntil(sseEventSender::sendPong)
            .map(_ -> ResponseEntity.ok().<Void>build())
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> pong(final String appIdentifier, final ServerWebExchange exchange) {
//        log.info("{} pong() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
//            .filter(ca -> ca.appIdentifier().isPresent())
//            .flatMap(auth -> sseSessionRepository.findByIdAndCreator(auth.appIdentifier(), auth.userId()))
//            .doOnNext(sse::pong)
            .map(CardServerAuthentication::appIdentifier)
            .map(sseSessionRepository::pong)
//            .retryWhen(Retry.backoff(5, Duration.ofMillis(100)) // 3 attempts, exponential backoff
//                .filter(this::isOptimisticLockingError)
//                .doBeforeRetry(signal -> log.warn("Retry saving session, retry: " + signal.totalRetries()))
//            )
            .map(_ -> ResponseEntity.ok().<Void>build())
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    private boolean isOptimisticLockingError(final Throwable ex) {
        // Spring Data maps the Couchbase CAS mismatch to OptimisticLockingFailureException
        return ex instanceof OptimisticLockingFailureException;
    }

    @Override
    public Mono<ResponseEntity<Flux<User>>> getUserByIds(final String appIdentifier, final List<String> userIds, final ServerWebExchange exchange) {
        log.info("{} getUserByIds() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
            .map((_user) -> ResponseEntity.ok(userService.getUsers(userIds).mapNotNull(userToOpenApiConverter::convert)))
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
                .map(count -> new CreateInviteResponse().count(count))
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
            .delayUntil(auth -> postMessageMono.flatMap(postMessage -> userService.usersMessage(auth.userId(), postMessage.getRecipients(), postMessage.getMessage())))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.<Void>status(HttpStatus.UNAUTHORIZED).build());
    }

}
