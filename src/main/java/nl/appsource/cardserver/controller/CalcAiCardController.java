package nl.appsource.cardserver.controller;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.GameToOpenApiConverter;
import nl.appsource.cardserver.service.AiPlayerNew;
import org.openapitools.api.CalcAiCardApi;
import org.openapitools.model.CalcAiCardRequest;
import org.openapitools.model.Card;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static nl.appsource.cardserver.converter.GameToOpenApiConverter.convertToModel;

@Slf4j
@RestController
public class CalcAiCardController implements CalcAiCardApi {

    @Override
    public Mono<ResponseEntity<Card>> calcAiCard(final Mono<CalcAiCardRequest> calcAiCardRequestArg, final ServerWebExchange exchange) {
        return calcAiCardRequestArg
            .doOnNext((calcAiCardRequest) -> log.info("{} calcAiCard()", exchange.getRequest().getRemoteAddress()))
            .map(calcAiCardRequest -> new AiPlayerNew(convertToModel(calcAiCardRequest.getCurrentTrick()), GameToOpenApiConverter.convertSuit(calcAiCardRequest.getTrumpSuit()), AiPlayerNew.Hand.from(convertToModel(calcAiCardRequest.getHand())), calcAiCardRequest.getGameVariant()).calcAiCard())
            .map(GameToOpenApiConverter::convertCard)
            .map(ResponseEntity::ok);

    }
}
