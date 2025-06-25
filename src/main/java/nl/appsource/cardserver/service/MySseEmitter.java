package nl.appsource.cardserver.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.GameToOpenApiConverter;
import nl.appsource.cardserver.filter.LoggingFilter;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.event.OnlineListEvent;
import nl.appsource.cardserver.model.event.PlayCardEvent;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Slf4j
@Getter
public final class MySseEmitter {

    private final String userId;

    private final SseEmitter emitter;

    private final UUID uuid = UUID.randomUUID();

    private Instant ping;

    private Instant pong;

    public MySseEmitter(final String userIdArg) {

        this.userId = userIdArg;
        this.emitter = new SseEmitter(Long.MAX_VALUE);

        emitter.onCompletion(() -> {
//            log.info("onCompletion() Removing an emitier");
            throw new RuntimeException();
        });
        emitter.onTimeout(() -> {
//            log.info("onTimeout() Removing an emitier");
            complete();
            throw new RuntimeException();
        });

        emitter.onError(throwable -> {
//            log.error("onError() Removing an emitter: {}:{}", throwable.getClass().getName(), throwable.getMessage());
            complete();
            throw new RuntimeException();
        });

        LoggingFilter.requestLogMessage(", new MySseEmitter(), uuid=" + uuid);
    }

    public void complete() {
        try {
            emitter.complete();
        } catch (Throwable t) {
            log.error("onComplete() Error: {}", t.getMessage());
        }
    }

    public boolean sendCardServerMessage(final String fromString, final String message) {
        return internalSend("cardservermessage", fromString + ": " + message);
    }

    public boolean sendPing() {
        LoggingFilter.requestLogMessage("sendPing " + uuid);
        return internalSend("ping", uuid.toString());
    }

    private boolean sendPong() {
        LoggingFilter.requestLogMessage(", sending pong " + uuid);
        return internalSend("pong", uuid.toString());
    }

    public boolean ping() {
        LoggingFilter.requestLogMessage(", got ping " + uuid);
        ping = Instant.now();
        return sendPong();
    }

    public boolean pong() {
        LoggingFilter.requestLogMessage(", got pong " + uuid);
        pong = Instant.now();
        return true;
    }

    private boolean internalSend(final String event) {
        return internalSend(event, null, null);
    }

    private boolean internalSend(final String event, final Object data) {
        return internalSend(event, data, null);
    }

    private boolean internalSend(final String event, final Object data, final MediaType mediaType) {
        try {
//            log.info("internalSend() sending event '{}' data: '{}' ", event, data);
            final SseEmitter.SseEventBuilder builder = SseEmitter.event().id(UUID.randomUUID().toString()).reconnectTime(3000).name(event);

            if (data != null) {
                builder.data(data);
            } else {
                builder.data("{}");
            }
            emitter.send(builder.build());
            return true;
        } catch (final Throwable e) {
            log.error("{}: event {} to {} data {}", e.getClass().getName() + ":" + e.getMessage(), event, userId, data, e);
            try {
                complete();
            } catch (final Throwable t) {

            }
            return false;
        }
    }

    public boolean playCard(final String userIdPlayer, final String gameId, final Card card) {
        final PlayCardEvent playCardEvent = new PlayCardEvent(userIdPlayer, gameId, GameToOpenApiConverter.convertCard(card));
        return internalSend("playCard", playCardEvent, APPLICATION_JSON);
    }

    public boolean sendOnline(final List<String> onlineList) {
        final OnlineListEvent onlineListEvent = new OnlineListEvent();
        onlineListEvent.setOnlineList(onlineList);
        return internalSend("online", onlineListEvent, APPLICATION_JSON);
    }

    public boolean sendUpdateFriends() {
        return internalSend("updateFriends");
    }

    public boolean sendUpdateGames() {
        return internalSend("updateGames");
    }

}

