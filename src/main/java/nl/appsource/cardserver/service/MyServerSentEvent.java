package nl.appsource.cardserver.service;

import org.openapitools.model.Boom;
import org.openapitools.model.Game;
import org.openapitools.model.HelloEvent;
import org.openapitools.model.OnlineListEvent;
import org.openapitools.model.User;

import java.io.Serializable;

public record MyServerSentEvent(String event, Object data) implements Serializable {

    public static MyServerSentEvent updateUser(final User user) {
        return new MyServerSentEvent("updateUser", user);
    }

    public static MyServerSentEvent ping() {
        return new MyServerSentEvent("ping", null);
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

    public static MyServerSentEvent end() {
        return new MyServerSentEvent("end", null);
    }

    public static MyServerSentEvent onlineList(final OnlineListEvent onlineListEvent) {
        return new MyServerSentEvent("onlineList", onlineListEvent);
    }

}
