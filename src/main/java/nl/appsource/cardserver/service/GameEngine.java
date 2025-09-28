package nl.appsource.cardserver.service;

import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.service.exception.GameEngineException;
import reactor.core.publisher.Mono;

import java.util.List;

public interface GameEngine {

    List<Card> getTrickCards(int trickNr);

    int determineTrickWinner(int trickNr);

    int calcWhoSay() throws GameEngineException;

    int calcWhoHasTurn() throws GameEngineException;

    Mono<GameEngine> sayAi() throws GameEngineException;

    Mono<GameEngine> playCard(String userId, Card card) throws GameEngineException;

    Mono<GameEngine> say(String userId, Boolean say) throws GameEngineException;

    Mono<GameEngine> playAiCard() throws GameEngineException;

    boolean isCompleted();

    int calcTricksPlayed();

    boolean isFullTrick();

    boolean isAiTurn();

    boolean isAiSay();

    Game getGame();

    int getTurnCount();

    Mono<GameEngine> openLastTrick();

    Mono<GameEngine> closeLastTrick();

    boolean isLastTrick();

    String getPartner(String userId);

    String getTrickWinnerId(List<Card> currentTrick);

    int calculateTrickPoints(int trickNr);

    int calculateTrickRoem(int trickNr);

    Boolean getErIsGegaan();
}
