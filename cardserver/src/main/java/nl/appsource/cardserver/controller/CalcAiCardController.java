//package nl.appsource.cardserver.controller;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import nl.appsource.cardserver.converter.GameToOpenApiConverter;
//import nl.appsource.cardserver.model.Game;
//import nl.appsource.cardserver.model.Suit;
//import nl.appsource.cardserver.repository.GameRepository;
//import nl.appsource.cardserver.service.AiPlayerNew;
//import nl.appsource.cardserver.service.GameEngine;
//import nl.appsource.cardserver.service.GameEngineImpl;
//import org.openapitools.api.AiApi;
//import nl.appsource.generated.openapi.model.AiCreateGameRequest;
//import nl.appsource.generated.openapi.model.AiPlayCardRequest;
//import nl.appsource.generated.openapi.model.CalcAiCardRequest;
//import org.springframework.context.annotation.Profile;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.RestController;
//import org.springframework.web.server.ServerWebExchange;
//import reactor.core.publisher.Mono;
//
//import java.security.SecureRandom;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.Random;
//
//import static nl.appsource.cardserver.converter.GameToOpenApiConverter.convertCard;
//import static nl.appsource.cardserver.converter.GameToOpenApiConverter.convertSuit;
//import static nl.appsource.cardserver.converter.GameToOpenApiConverter.convertToModel;
//import static nl.appsource.cardserver.service.GameEngineImpl.AI_USER_ID;
//import static nl.appsource.cardserver.service.GameServiceImpl.randomCards;
//import static nl.appsource.cardserver.utils.IDTYPE.GAME;
//import static nl.appsource.cardserver.utils.Utils.idGen;
//
//@Slf4j
//@RestController
//@Profile("never")
//@RequiredArgsConstructor
//public class CalcAiCardController implements AiApi {
//
//    private static final Random RAND = new SecureRandom();
//
//    private final GameToOpenApiConverter gameToOpenApiConverter;
//
//    private final GameRepository gameRepository;
//
//    @Override
//    public Mono<ResponseEntity<nl.appsource.generated.openapi.model.Game>> aiCreateGame(final Mono<AiCreateGameRequest> aiCreateGameRequestArg, final ServerWebExchange exchange) {
//        log.info("aiCreateGame()");
//        return aiCreateGameRequestArg.map(aiCreateGameRequest -> {
//                final Game game = new Game();
//                game.setId(idGen(GAME, 20));
//                game.setCreator(AI_USER_ID.getFirst());
//                game.setPlayers(new ArrayList<>(AI_USER_ID));
//                game.setDealer(0);
//                game.setSay(new HashMap<>());
//                game.setTurns(new ArrayList<>());
//                game.setPlayerCard(randomCards());
//                game.setTrump(Suit.values()[RAND.nextInt(Suit.values().length)]);
//                game.setLastTrickOpen(false);
//                game.setGameVariant(aiCreateGameRequest.getGameVariant());
//                game.setDealCounter(0);
//                return game;
//            })
//            .flatMap(gameRepository::updatedSave)
//            .map(gameToOpenApiConverter::convert)
//            .map(ResponseEntity::ok);
//    }
//
//
//    @Override
//    public Mono<ResponseEntity<nl.appsource.generated.openapi.model.Game>> aiPlayCard(final Mono<AiPlayCardRequest> aiPlayCardRequestArg, final ServerWebExchange exchange) {
//        return aiPlayCardRequestArg
//            .flatMap(aiPlayCardRequest -> {
//                log.info("aiPlayCard() gameId={} playerId={} card={}", aiPlayCardRequest.getGameId(), aiPlayCardRequest.getPlayerId(), aiPlayCardRequest.getCard());
//                return gameRepository.findById(aiPlayCardRequest.getGameId())
//                    .map(GameEngineImpl::new)
//                    .flatMap(gameEngine -> gameEngine.playCard(aiPlayCardRequest.getPlayerId(), convertCard(aiPlayCardRequest.getCard())))
//                    .map(GameEngine::getGame);
//            })
//            .flatMap(gameRepository::updatedSave)
//            .mapNotNull(gameToOpenApiConverter::convert)
//            .map(ResponseEntity::ok);
//    }
//
//    @Override
//    public Mono<ResponseEntity<nl.appsource.generated.openapi.model.Card>> calcAiCard(final Mono<CalcAiCardRequest> calcAiCardRequestArg, final ServerWebExchange exchange) {
//        log.info("calcAiCard()");
//        return calcAiCardRequestArg
//            .doOnNext((calcAiCardRequest) -> log.info("{} calcAiCard()", exchange.getRequest().getRemoteAddress()))
//            .map(calcAiCardRequest -> new AiPlayerNew(convertToModel(calcAiCardRequest.getCurrentTrick()), convertSuit(calcAiCardRequest.getTrumpSuit()), AiPlayerNew.Hand.from(convertToModel(calcAiCardRequest.getHand())), calcAiCardRequest.getGameVariant()).calcAiCard())
//            .map(GameToOpenApiConverter::convertCard)
//            .map(ResponseEntity::ok);
//    }
//
//}
