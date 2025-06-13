package nl.appsource.cardserver.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.User;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
public final class MySseEmitter {

    @Getter
    private final String userId;

    @Getter
    private final SseEmitter emitter;

    public MySseEmitter(final String userIdArg, final Consumer<SseEmitter> removeCallbackArg) {

        this.userId = userIdArg;
        this.emitter = new SseEmitter(Long.MAX_VALUE);

        emitter.onCompletion(() -> {
            log.info("onCompletion() Removing an emitier");
            removeCallbackArg.accept(emitter);
        });
        emitter.onTimeout(() -> {
            log.info("onTimeout() Removing an emitier");
            emitter.complete();
            removeCallbackArg.accept(emitter);
        });

        emitter.onError(throwable -> {
            log.error("onError() Removing an emitter: {}:{}", throwable.getClass().getName(), throwable.getMessage());
            removeCallbackArg.accept(emitter);
        });

    }

    public void complete() {
        try {
            emitter.complete();
        } catch (Throwable t) {
            log.error("onComplete() Error: {}", t.getMessage());
        }
    }

    public void send(final User fromUser, final String message, final Consumer<SseEmitter> removeCallbackArg) {
        try {
            log.info("sending {} from: {} to: {}", message, fromUser.getDisplayName(), userId);
            emitter.send(SseEmitter.event().id(UUID.randomUUID().toString()).reconnectTime(1000).name("cardservermessage").data(fromUser.getDisplayName() + ":" + message).build());
        } catch (final Throwable e) {
            log.error("{}: from {} to {} message {}", e.getClass().getName() + ":" + e.getMessage(), fromUser.getDisplayName(), userId, message);
            removeCallbackArg.accept(emitter);
            try {
                emitter.complete();
            } catch (final Throwable e1) {
                log.error("", e1);
            }
        }

    }
}

