package nl.appsource.cardserver.service;

import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;

public interface GameEngine {
    Game playCard(Card card);
}
