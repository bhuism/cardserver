package nl.appsource.cardserver.service;

import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.service.exception.GameEngineException;
import org.openapitools.model.UserMessage;

import java.util.List;

public interface GameEngine {

    int calcWhoSay() throws GameEngineException;

    int calcWhoHasTurn() throws GameEngineException;

    List<UserMessage> playCard(String userId, Card card) throws GameEngineException;

    List<UserMessage> say(String userId, Boolean say) throws GameEngineException;

    void playAiCard() throws GameEngineException;

    boolean isCompleted();

    Game getGame();

    boolean hasFullTrick();

    boolean isAiTurn() throws GameEngineException;

    boolean isAiSay() throws GameEngineException;
}
