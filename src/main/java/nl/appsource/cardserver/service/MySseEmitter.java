package nl.appsource.cardserver.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.filter.LoggingFilter;
import nl.appsource.cardserver.model.event.GameStateEvent;
import nl.appsource.cardserver.model.event.OnlineListEvent;
import org.openapitools.model.Game;
import org.springframework.http.codec.ServerSentEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Getter
public final class MySseEmitter {

    private final String userId;

    private final UUID uuid = UUID.randomUUID();

    private Instant ping;

    private Instant pong;

    public MySseEmitter(final String userIdArg) {
        this.userId = userIdArg;
    }


    public UserServerSentEvent sendCardServerMessage(final String fromString, final String message) {
        return internalSend("cardservermessage", fromString + ": " + message);
    }

    public UserServerSentEvent sendPing() {
        LoggingFilter.requestLogMessage(", sendPing " + uuid);
        return internalSend("ping", "{ \"uuid\": \"" + uuid + "\"}");
    }

    private UserServerSentEvent sendPong() {
        LoggingFilter.requestLogMessage(", sending pong " + uuid);
        return internalSend("pong", "{ \"uuid\": \"" + uuid + "\"}");
    }

    public UserServerSentEvent receivePing() {
        LoggingFilter.requestLogMessage(", got ping " + uuid);
        ping = Instant.now();
        return sendPong();
    }

    public void receivePong() {
        LoggingFilter.requestLogMessage(", got pong " + uuid);
        pong = Instant.now();
    }

    private UserServerSentEvent internalSend(final String event) {
        return internalSend(event, null);
    }

    private UserServerSentEvent internalSend(final String event, final Object data) {
        if (log.isTraceEnabled()) {
            log.trace("internalSend() sending event '{}' data: '{}' ", event, data);
        }

        final Instant now = Instant.now();
        final String id = "" + (now.getEpochSecond() * 1000000 + now.getNano());

        return new UserServerSentEvent(uuid, ServerSentEvent.builder().event(event).id(id).data(data == null ? "{}" : data).build());
    }

    public UserServerSentEvent gameChanged(final Game game) {
        final GameStateEvent playCardEvent = new GameStateEvent(game);
        return internalSend("gameStateUpdate", playCardEvent);
    }

    public UserServerSentEvent createOnlineEvent(final List<String> onlineList) {
        final OnlineListEvent onlineListEvent = new OnlineListEvent();
        onlineListEvent.setOnlineList(onlineList);
        log.info("Sending online friends: " + onlineList);
        return internalSend("online", onlineListEvent);
    }

    public UserServerSentEvent sendUpdateFriends() {
        return internalSend("updateFriends");
    }

    public UserServerSentEvent sendUpdateGames() {
        return internalSend("updateGames");
    }

}

