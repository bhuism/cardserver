package nl.appsource.cardserver.openapi;

import nl.appsource.generated.openapi.model.Boom;
import nl.appsource.generated.openapi.model.Game;
import nl.appsource.generated.openapi.model.MessageEvent;
import nl.appsource.generated.openapi.model.OnlineListEvent;
import nl.appsource.generated.openapi.model.User;

import java.io.Serializable;
import java.util.UUID;

public record MyServerSentEvent<T>(String event, T data, UUID uuid) implements Serializable {

    public MyServerSentEvent(final String event, final Object data) {
        this(event, (T) data, UUID.randomUUID());
    }

    public MyServerSentEvent(final String event) {
        this(event, (T) "{}");
    }

    public static MyServerSentEvent<User> updateUser(final User user) {
        return new MyServerSentEvent<>("updateUser", user);
    }

    public static MyServerSentEvent<Game> updateGame(final Game game) {
        return new MyServerSentEvent<>("updateGame", game);
    }

    public static MyServerSentEvent<Boom> updateBoom(final Boom boom) {
        return new MyServerSentEvent<>("updateBoom", boom);
    }

    public static MyServerSentEvent<OnlineListEvent> onlineList(final OnlineListEvent onlineListEvent) {
        return new MyServerSentEvent<>("onlineList", onlineListEvent);
    }

    public static MyServerSentEvent<MessageEvent> messageEvent(final MessageEvent messageEvent) {
        return new MyServerSentEvent<>("messageEvent", messageEvent);
    }

    public static MyServerSentEvent<Void> startCache() {
        return new MyServerSentEvent<>("startCache");
    }

    public static MyServerSentEvent<Void> endCache() {
        return new MyServerSentEvent<>("endCache");
    }

//    public static MyServerSentEvent newGame(final NewGameEvent newGameEvent) {
//        return new MyServerSentEvent("newGame", newGameEvent);
//    }

//    public static MyServerSentEvent gameEvent(final GameEvent gameEvent) {
//        return new MyServerSentEvent("gameEvent", gameEvent);
//    }
}
