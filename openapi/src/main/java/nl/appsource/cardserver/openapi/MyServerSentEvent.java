package nl.appsource.cardserver.openapi;

import nl.appsource.generated.openapi.model.Boom;
import nl.appsource.generated.openapi.model.Game;
import nl.appsource.generated.openapi.model.MessageEvent;
import nl.appsource.generated.openapi.model.OnlineListEvent;
import nl.appsource.generated.openapi.model.User;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static java.util.Collections.emptySet;

public record MyServerSentEvent<T>(String event, T data, Set<String> userIds, UUID uuid) implements Serializable {

    public MyServerSentEvent(final String event, final Object data, final Set<String> userIds) {
        this(event, (T) data, userIds, UUID.randomUUID());
    }

    public MyServerSentEvent(final String event, final Set<String> userIds) {
        this(event, (T) "{}", userIds);
    }

    public static MyServerSentEvent<User> updateUser(final User user, final Set<String> userIds) {
        return new MyServerSentEvent<>("updateUser", user, userIds);
    }

    public static MyServerSentEvent<Game> updateGame(final Game game) {
        return new MyServerSentEvent<>("updateGame", game, new HashSet<>(game.getPlayers()));
    }

    public static MyServerSentEvent<Boom> updateBoom(final Boom boom) {
        return new MyServerSentEvent<>("updateBoom", boom, new HashSet<>(boom.getPlayers()));
    }

    public static MyServerSentEvent<OnlineListEvent> onlineList(final OnlineListEvent onlineListEvent, final Set<String> userIds) {
        return new MyServerSentEvent<>("onlineList", onlineListEvent, userIds);
    }

    public static MyServerSentEvent<MessageEvent> messageEvent(final MessageEvent messageEvent, final Set<String> userIds) {
        return new MyServerSentEvent<>("messageEvent", messageEvent, userIds);
    }

    public static MyServerSentEvent<Void> startCache() {
        return new MyServerSentEvent<>("startCache", emptySet());
    }

    public static MyServerSentEvent<Void> endCache() {
        return new MyServerSentEvent<>("endCache", emptySet());
    }

//    public static MyServerSentEvent newGame(final NewGameEvent newGameEvent) {
//        return new MyServerSentEvent("newGame", newGameEvent);
//    }

//    public static MyServerSentEvent gameEvent(final GameEvent gameEvent) {
//        return new MyServerSentEvent("gameEvent", gameEvent);
//    }
}
