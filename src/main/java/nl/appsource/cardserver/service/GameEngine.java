package nl.appsource.cardserver.service;

import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.service.exception.CardAlreadyPlayerException;
import nl.appsource.cardserver.service.exception.GameCompletedException;

public interface GameEngine {
    int calcWhoHasTurn();

//    boolean isAiPlayerAanslag();

    void playCard(String userId, Card card) throws GameCompletedException, CardAlreadyPlayerException;

    boolean isCompleted();

//    void playAiCard();

    Game getGame();

    Card calcAiCard(String userId);

    boolean hasFullTrick();

    boolean isAiTurn();
}
