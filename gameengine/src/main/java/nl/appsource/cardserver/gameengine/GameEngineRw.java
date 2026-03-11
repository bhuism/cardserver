package nl.appsource.cardserver.gameengine;

import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.Game;
import reactor.core.publisher.Mono;

public interface GameEngineRw {

    Mono<Game> playCard(Card card);

    Mono<Game> say(Boolean say);

    Mono<Game> openLastTrick();

    Mono<Game> closeLastTrick();

    Mono<Game> claimRoem();

}
