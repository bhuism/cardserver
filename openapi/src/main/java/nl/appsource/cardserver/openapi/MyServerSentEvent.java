package nl.appsource.cardserver.openapi;

import nl.appsource.generated.openapi.model.Boom;
import nl.appsource.generated.openapi.model.Game;
import nl.appsource.generated.openapi.model.HelloEvent;
import nl.appsource.generated.openapi.model.MessageEvent;
import nl.appsource.generated.openapi.model.NewGameEvent;
import nl.appsource.generated.openapi.model.OnlineListEvent;
import nl.appsource.generated.openapi.model.User;

import java.io.Serializable;

public record MyServerSentEvent(String event, Object data) implements Serializable {

    public MyServerSentEvent(final String event) {
        this(event, "{}");
    }

    public static MyServerSentEvent updateUser(final User user) {
        return new MyServerSentEvent("updateUser", user);
    }

    public static MyServerSentEvent ping(final long count) {
        return new MyServerSentEvent("ping", "{ count: " + count + "}");
    }

    public static MyServerSentEvent pong() {
        return new MyServerSentEvent("pong", null);
    }

    public static MyServerSentEvent updateGame(final Game game) {
        return new MyServerSentEvent("updateGame", game);
    }

    public static MyServerSentEvent updateBoom(final Boom boom) {
        return new MyServerSentEvent("updateBoom", boom);
    }

    public static MyServerSentEvent updateBooms() {
        return new MyServerSentEvent("updateBooms");
    }

    public static MyServerSentEvent hello(final HelloEvent helloEvent) {
        return new MyServerSentEvent("hello", helloEvent);
    }

    public static MyServerSentEvent onlineList(final OnlineListEvent onlineListEvent) {
        return new MyServerSentEvent("onlineList", onlineListEvent);
    }

    public static MyServerSentEvent messageEvent(final MessageEvent messageEvent) {
        return new MyServerSentEvent("messageEvent", messageEvent);
    }

    public static MyServerSentEvent updateGames() {
        return new MyServerSentEvent("updateGames");
    }

    public static MyServerSentEvent updateFriends() {
        return new MyServerSentEvent("updateFriends");
    }

    public static MyServerSentEvent newGame(final NewGameEvent newGameEvent) {
        //return NewGameEvent.builder().displayNameCreator(game.getCreator()).gameId(game.getId()).build()

        return new MyServerSentEvent("newGame", newGameEvent);
    }
}
