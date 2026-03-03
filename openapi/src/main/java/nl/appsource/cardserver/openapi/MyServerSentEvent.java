package nl.appsource.cardserver.openapi;

import nl.appsource.generated.openapi.model.Boom;
import nl.appsource.generated.openapi.model.Game;
import nl.appsource.generated.openapi.model.HelloEvent;
import nl.appsource.generated.openapi.model.OnlineListEvent;
import nl.appsource.generated.openapi.model.User;

import java.io.Serializable;

public record MyServerSentEvent(String event, Object data) implements Serializable {

    public static MyServerSentEvent updateUser(final User user) {
        return new MyServerSentEvent("updateUser", user);
    }

    public static MyServerSentEvent ping() {
        return new MyServerSentEvent("ping", null);
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

    public static MyServerSentEvent hello(final HelloEvent helloEvent) {
        return new MyServerSentEvent("hello", helloEvent);
    }

    public static MyServerSentEvent onlineList(final OnlineListEvent onlineListEvent) {
        return new MyServerSentEvent("onlineList", onlineListEvent);
    }

}
