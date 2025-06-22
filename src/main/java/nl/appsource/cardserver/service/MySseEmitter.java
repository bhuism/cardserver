package nl.appsource.cardserver.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.GameToOpenApiConverter;
import nl.appsource.cardserver.filter.LoggingFilter;
import nl.appsource.cardserver.model.Card;
import nl.appsource.cardserver.model.PlayCardEvent;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Getter
public final class MySseEmitter {

    private final String userId;

    private final SseEmitter emitter;

    private final UUID uuid = UUID.randomUUID();

    private Instant pinged;

    private Instant ponged;

    public MySseEmitter(final String userIdArg) {

        this.userId = userIdArg;
        this.emitter = new SseEmitter(Long.MAX_VALUE);

        emitter.onCompletion(() -> {
            log.info("onCompletion() Removing an emitier");
            throw new RuntimeException();
        });
        emitter.onTimeout(() -> {
            log.info("onTimeout() Removing an emitier");
            complete();
            throw new RuntimeException();
        });

        emitter.onError(throwable -> {
            log.error("onError() Removing an emitter: {}:{}", throwable.getClass().getName(), throwable.getMessage());
            complete();
            throw new RuntimeException();
        });

        LoggingFilter.requestLogMessage(", new Emitter userId=" + userId + ", uuid=" + uuid);
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
        pinged = Instant.now();
        return sendPong();
    }

    public boolean pong() {
        LoggingFilter.requestLogMessage(", got pong " + uuid);
        ponged = Instant.now();
        return true;
    }

    private boolean internalSend(final String event, final Object data) {
        return internalSend(event, data, null);
    }

    private boolean internalSend(final String event, final Object data, final MediaType mediaType) {
        try {
//            log.info("internalSend() sending event '{}' data: '{}' ", event, data);
            emitter.send(SseEmitter.event().id(UUID.randomUUID().toString()).reconnectTime(3000).name(event).data(data, mediaType).build());
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

    public void playCard(final String userIdPlayer, final String gameId, final Card card) {
        final PlayCardEvent playCardEvent = new PlayCardEvent(userIdPlayer, gameId, GameToOpenApiConverter.convertCard(card));
        internalSend("playCard", playCardEvent, MediaType.APPLICATION_JSON);
    }
}

