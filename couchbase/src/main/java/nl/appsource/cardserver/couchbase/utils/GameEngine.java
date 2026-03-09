package nl.appsource.cardserver.couchbase.utils;

import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;

import java.util.List;

public interface GameEngine {

    List<Card> getTrickCards(int trickNr);

    int determineTrickWinningPlayer(int trickNr);

    int calcWhoSay();

    int calcWhoHasTurn();

    boolean isCompleted();

    int calcTricksPlayed();

    boolean isFullTrick();

    int whoHasCard(Card card);

    boolean isAiTurn();

    boolean isAiSay();

    Game getGame();

    boolean isErGegaan();

    boolean niemandIsGegaanEnIedereenHeeftGezegd();

    boolean iedereenHeeftGezegd();

    int getTurnCount();

    boolean isLastTrick();

    String getPartner(String userId);

    String getTrickWinnerId(List<Card> currentTrick);

    int calculateTrickPoints(int trickNr);

    int calculateTrickRoem(int trickNr);

    Boolean getErIsGegaan();

    List<Card> getHuidigeTableCards();

    Boolean verzaakt(int correctedSlagNr, int spelerId);


}
