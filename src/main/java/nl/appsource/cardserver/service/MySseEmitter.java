package nl.appsource.cardserver.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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
        return internalSend("ping", uuid);
    }

    private boolean sendPong() {
        return internalSend("pong", uuid);
    }

    public boolean ping() {
        pinged = Instant.now();
        return sendPong();
    }

    public boolean pong() {
        ponged = Instant.now();
        return true;
    }

    /**
     * @param event
     * @param data
     * @return if error
     */
    private boolean internalSend(final String event, final Object data) {
        try {
            log.info("sending {} data: {} ", event, data);
            emitter.send(SseEmitter.event().id(UUID.randomUUID().toString()).reconnectTime(3000).name(event).data(data).build());
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
}

