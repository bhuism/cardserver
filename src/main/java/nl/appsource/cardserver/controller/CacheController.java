package nl.appsource.cardserver.controller;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.Feedback;
import nl.appsource.cardserver.repository.FeedbackRepository;
import nl.appsource.cardserver.repository.SseSessionRepository;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.UserService;
import org.openapitools.api.FeedbackApi;
import org.openapitools.model.FeedbackRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static nl.appsource.cardserver.utils.IDTYPE.FEED;
import static nl.appsource.cardserver.utils.Utils.idGen;

@RestController
@Slf4j
public class CacheController extends GenericController implements V1Api, FeedbackApi {

    private final FeedbackRepository feedBackRepository;

    public CacheController(final SseSessionRepository sseSessionRepository, final UserRepository userRepository, final FeedbackRepository feedbackRepository, final UserService userService) {
        super(userRepository, sseSessionRepository, userService);
        this.feedBackRepository = feedbackRepository;
    }

    @Override
    public Mono<ResponseEntity<Void>> feedback(final String appIdentifier, final Mono<FeedbackRequest> feedbackRequest, final ServerWebExchange exchange) {
        log.info("{} feedback() appIdentifier={}", exchange.getRequest().getRemoteAddress(), appIdentifier);
        return authorize(appIdentifier, exchange)
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
