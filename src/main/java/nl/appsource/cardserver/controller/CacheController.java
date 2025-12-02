package nl.appsource.cardserver.controller;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.Feedback;
import nl.appsource.cardserver.repository.FeedbackRepository;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.SseEmitterRepository;
import org.openapitools.api.FeedbackApi;
import org.openapitools.api.ReloadCacheApi;
import org.openapitools.model.FeedbackRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static nl.appsource.cardserver.service.GameServiceImpl.idGen;

@RestController
@Slf4j
public class CacheController extends GenericController implements ReloadCacheApi, V1Api, FeedbackApi {

    private final FeedbackRepository feedBackRepository;

    public CacheController(final SseEmitterRepository sseEmitterRepository, final UserRepository userRepositoryArg, final FeedbackRepository feedbackRepositoryArg) {
        super(sseEmitterRepository, userRepositoryArg);
        this.feedBackRepository = feedbackRepositoryArg;
    }

    @Override
    public Mono<ResponseEntity<Void>> reloadCache(final UUID appIdentifier, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} reloadCache() userId={}", exchange.getRequest().getRemoteAddress(), user.getId()))
            .doOnNext(user -> sseEmitterRepository.reloadCache(appIdentifier, user.getId()))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public Mono<ResponseEntity<Void>> feedback(final UUID appIdentifier, final Mono<FeedbackRequest> feedbackRequest, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((user) -> log.info("{} feedback() userId={}", exchange.getRequest().getRemoteAddress(), user.getId()))
            .flatMap(user -> feedbackRequest.flatMap((fb) -> {

                final Feedback feedBack = new Feedback();

                feedBack.setId(idGen(20));
                feedBack.setCreator(user.getId());
                feedBack.setUpdated(Instant.now());
                feedBack.setCreated(Instant.now());

                feedBack.setText(fb.getMessage());

                return feedBackRepository.save(feedBack);

                }))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
