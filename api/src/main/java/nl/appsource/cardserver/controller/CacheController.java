package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.couchbase.repository.FeedbackRepository;
import nl.appsource.cardserver.model.Feedback;
import nl.appsource.generated.openapi.model.FeedbackRequest;
import org.openapitools.api.FeedbackApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static nl.appsource.cardserver.utils.IDTYPE.FEED;
import static nl.appsource.cardserver.utils.Utils.idGen;

@RequiredArgsConstructor
@RestController
@Slf4j
public class CacheController extends AbstractBaseController implements V1Api, FeedbackApi {

    private final FeedbackRepository feedBackRepository;

    @Override
    public Mono<ResponseEntity<Void>> feedback(final Mono<FeedbackRequest> feedbackRequest, final ServerWebExchange exchange) {
        log.info("{} feedback()", exchange.getRequest().getRemoteAddress());
        return getUserId(exchange)
            .flatMap(_ -> feedbackRequest.flatMap((fb) -> {

                final Feedback feedBack = new Feedback();

                feedBack.setId(idGen(FEED, 20));
                feedBack.setText(fb.getMessage());

                return feedBackRepository.save(feedBack);

            }))
            .then(Mono.<ResponseEntity<Void>>just(ResponseEntity.ok().build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
