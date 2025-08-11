package nl.appsource.cardserver.service;

import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.service.exception.CardAlreadyPlayerException;
import nl.appsource.cardserver.service.exception.GameCompletedException;
import nl.appsource.cardserver.service.exception.GameEngineException;
import org.openapitools.model.UserMessage;

import java.util.List;

public interface GameEngine {

    int calcWhoSay() throws GameEngineException;

    int calcWhoHasTurn() throws GameEngineException;

    List<UserMessage> playCard(String userId, Card card) throws GameEngineException;

    boolean playAiCard() throws GameEngineException;

    boolean isCompleted();

    Game getGame();

    Card calcAiCard(String userId) throws GameEngineException;

    boolean hasFullTrick();

    boolean isAiTurn() throws GameEngineException;
}
