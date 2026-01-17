package nl.appsource.cardserver.controller;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.Feedback;
import nl.appsource.cardserver.repository.FeedbackRepository;
import nl.appsource.cardserver.repository.SseSessionRepository;
import nl.appsource.cardserver.repository.UserRepository;
import org.openapitools.api.FeedbackApi;
import org.openapitools.model.FeedbackRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static nl.appsource.cardserver.utils.IDTYPE.FEED;
import static nl.appsource.cardserver.utils.Utils.idGen;

@RestController
@Slf4j
public class CacheController extends GenericController implements V1Api, FeedbackApi {

    private final FeedbackRepository feedBackRepository;

    public CacheController(final SseSessionRepository sseSessionRepository, final UserRepository userRepositoryArg, final FeedbackRepository feedbackRepositoryArg) {
        super(userRepositoryArg, sseSessionRepository);
        this.feedBackRepository = feedbackRepositoryArg;
    }

//    @Override
//    public Mono<ResponseEntity<Void>> reloadCache(final Optional<String> appIdentifier, final ServerWebExchange exchange) {
//        return authorize(appIdentifier, exchange)
//            .doOnNext((auth) -> log.info("{} reloadCache() userId={}", exchange.getRequest().getRemoteAddress(), auth.user().getId()))
//            .doOnNext(auth -> sseEmitterRepository.reloadCache(appIdentifier, auth.user().getId()))
//            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
//            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
//    }

    @Override
    public Mono<ResponseEntity<Void>> feedback(final Mono<FeedbackRequest> feedbackRequest, final Optional<String> appIdentifier, final ServerWebExchange exchange) {
        return authorize(appIdentifier, exchange)
            .doOnNext((auth) -> log.info("{} feedback() userId={}", exchange.getRequest().getRemoteAddress(), auth.user().getId()))
            .flatMap(user -> feedbackRequest.flatMap((fb) -> {

                final Feedback feedBack = new Feedback();

                feedBack.setId(idGen(FEED, 20));
                feedBack.setText(fb.getMessage());

                return feedBackRepository.save(feedBack);

                }))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
