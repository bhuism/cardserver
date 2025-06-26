package nl.appsource.cardserver.service;

import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.service.exception.CardAlreadyPlayerException;
import nl.appsource.cardserver.service.exception.GameCompletedException;

public interface GameEngine {
    Game playCard(Card card) throws GameCompletedException, CardAlreadyPlayerException;
}
