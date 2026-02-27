package nl.appsource.cardserver.couchbase.utils;

import nl.appsource.cardserver.couchbase.model.Card;
import nl.appsource.cardserver.couchbase.model.Game;
import reactor.core.publisher.Mono;

import java.util.List;

public interface GameEngine {

    List<Card> getTrickCards(int trickNr);

//    Card determineTrickWinningCard(List<Card> trick);

    int determineTrickWinningPlayer(int trickNr);

    int calcWhoSay();

    int calcWhoHasTurn();

    Mono<GameEngine> sayAi();

    Mono<GameEngine> playCard(String userId, Card card);

    Mono<GameEngine> say(String userId, Boolean say);

    Mono<GameEngine> playAiCard();

    Mono<GameEngine> checkNiemandIsGegaanEnIedereenHeeftGezegd();

    Mono<GameEngine> claimRoem(String userId);

    boolean isCompleted();

    int calcTricksPlayed();

    boolean isFullTrick();

    boolean isAiTurn();

    boolean isAiSay();

    Game getGame();

    boolean isErGegaan();

    boolean niemandIsGegaanEnIedereenHeeftGezegd();

    boolean iedereenHeeftGezegd();

    int getTurnCount();

    Mono<GameEngine> openLastTrick();

    Mono<GameEngine> closeLastTrick();

    boolean isLastTrick();

    String getPartner(String userId);

    String getTrickWinnerId(List<Card> currentTrick);

    int calculateTrickPoints(int trickNr);

    int calculateTrickRoem(int trickNr);

    Boolean getErIsGegaan();

    List<Card> getHuidigeTableCards();

    Boolean verzaakt(int correctedSlagNr, int spelerId);
}
