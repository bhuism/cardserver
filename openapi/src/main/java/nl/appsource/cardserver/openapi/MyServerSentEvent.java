package nl.appsource.cardserver.openapi;

import nl.appsource.generated.openapi.model.Boom;
import nl.appsource.generated.openapi.model.Game;
import nl.appsource.generated.openapi.model.MessageEvent;
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

    public static MyServerSentEvent updateGame(final Game game) {
        return new MyServerSentEvent("updateGame", game);
    }

    public static MyServerSentEvent updateBoom(final Boom boom) {
        return new MyServerSentEvent("updateBoom", boom);
    }

    public static MyServerSentEvent onlineList(final OnlineListEvent onlineListEvent) {
        return new MyServerSentEvent("onlineList", onlineListEvent);
    }

    public static MyServerSentEvent messageEvent(final MessageEvent messageEvent) {
        return new MyServerSentEvent("messageEvent", messageEvent);
    }

    public static MyServerSentEvent startCache() {
        return new MyServerSentEvent("startCache");
    }

    public static MyServerSentEvent endCache() {
        return new MyServerSentEvent("endCache");
    }

//    public static MyServerSentEvent newGame(final NewGameEvent newGameEvent) {
//        return new MyServerSentEvent("newGame", newGameEvent);
//    }

//    public static MyServerSentEvent gameEvent(final GameEvent gameEvent) {
//        return new MyServerSentEvent("gameEvent", gameEvent);
//    }
}
