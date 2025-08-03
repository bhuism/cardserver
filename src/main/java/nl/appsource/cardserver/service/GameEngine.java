package nl.appsource.cardserver.service;

import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.service.exception.CardAlreadyPlayerException;
import nl.appsource.cardserver.service.exception.GameCompletedException;
import org.openapitools.model.UserMessage;

import java.util.List;

public interface GameEngine {
    int calcWhoHasTurn();

//    boolean isAiPlayerAanslag();

    List<UserMessage> playCard(String userId, Card card) throws GameCompletedException, CardAlreadyPlayerException;

    boolean playAiCard();

    boolean isCompleted();

//    void playAiCard();

    Game getGame();

    Card calcAiCard(String userId);

    boolean hasFullTrick();

    boolean isAiTurn();
}
