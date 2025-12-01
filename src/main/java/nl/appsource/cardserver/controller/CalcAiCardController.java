package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.GameToOpenApiConverter;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.service.AiPlayerNew;
import org.openapitools.api.AiApi;
import org.openapitools.model.AiCreateGameRequest;
import org.openapitools.model.CalcAiCardRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;

import static nl.appsource.cardserver.converter.GameToOpenApiConverter.convertSuit;
import static nl.appsource.cardserver.converter.GameToOpenApiConverter.convertToModel;
import static nl.appsource.cardserver.service.GameEngineImpl.AI_USER_ID;
import static nl.appsource.cardserver.service.GameServiceImpl.idGen;
import static nl.appsource.cardserver.service.GameServiceImpl.randomCards;

@Slf4j
@RestController
@RequiredArgsConstructor
public class CalcAiCardController implements AiApi {

    private final GameController gameController;

    private final GameToOpenApiConverter gameToOpenApiConverter;

    @Override
    public Mono<ResponseEntity<org.openapitools.model.Game>> aiCreateGame(final Mono<AiCreateGameRequest> aiCreateGameRequestArg, final ServerWebExchange exchange) {
        return aiCreateGameRequestArg.map(aiCreateGameRequest -> {
                final Game game = new Game();
                game.setId(idGen(20));
                game.setCreator(AI_USER_ID.get(0));
                game.setCreated(Instant.now());
                game.setUpdated(Instant.now());
                game.setPlayers(new ArrayList<>(AI_USER_ID));
                game.setDealer(0);
                game.setSay(new HashMap<>());
                game.setTurns(new ArrayList<>());
                game.setPlayerCard(randomCards());
                game.setTrump(convertSuit(aiCreateGameRequest.getTrumpSuit()));
                game.setLastTrickOpen(false);
                game.setGameVariant(aiCreateGameRequest.getGameVariant());
                game.setDealCounter(0);
                return game;
            })
            .map(gameToOpenApiConverter::convert)
            .map(ResponseEntity::ok);
    }

    @Override
    public Mono<ResponseEntity<org.openapitools.model.Card>> calcAiCard(final Mono<CalcAiCardRequest> calcAiCardRequestArg, final ServerWebExchange exchange) {
        return calcAiCardRequestArg
            .doOnNext((calcAiCardRequest) -> log.info("{} calcAiCard()", exchange.getRequest().getRemoteAddress()))
            .map(calcAiCardRequest -> new AiPlayerNew(convertToModel(calcAiCardRequest.getCurrentTrick()), convertSuit(calcAiCardRequest.getTrumpSuit()), AiPlayerNew.Hand.from(convertToModel(calcAiCardRequest.getHand())), calcAiCardRequest.getGameVariant()).calcAiCard())
            .map(GameToOpenApiConverter::convertCard)
            .map(ResponseEntity::ok);
    }

}
