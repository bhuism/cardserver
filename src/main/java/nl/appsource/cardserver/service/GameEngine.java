package nl.appsource.cardserver.service;

import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.service.exception.GameEngineException;
import org.openapitools.model.UserMessage;

import java.util.List;

public interface GameEngine {

    int calcWhoSay() throws GameEngineException;

    int calcWhoHasTurn() throws GameEngineException;

    void sayAi() throws GameEngineException;

    List<UserMessage> playCard(String userId, Card card) throws GameEngineException;

    void say(String userId, Boolean say) throws GameEngineException;

    void playAiCard() throws GameEngineException;

    boolean isCompleted();

    int calcTricksPlayed();

    boolean hasFullTrick();

    boolean isAiTurn();

    boolean isAiSay();
}
