package nl.appsource.cardserver.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.filter.LoggingFilter;
import nl.appsource.cardserver.model.event.GameStateEvent;
import nl.appsource.cardserver.model.event.OnlineListEvent;
import org.openapitools.model.Game;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
public final class MySseEmitter {

    @Getter
    private final String userId;

    @Getter
    private final UUID uuid = UUID.randomUUID();

    @Getter
    private Instant ping;

    @Getter
    private Instant pong;

    @Getter
    private Sinks.EmitResult errorEmitResult;

    private final Sinks.Many<UserServerSentEvent> manySinks = Sinks.many().unicast().onBackpressureError();

    public MySseEmitter(final String userIdArg) {
        this.userId = userIdArg;
    }

    public void sendCardServerMessage(final String fromString, final String message) {
        internalSend("cardservermessage", fromString + ": " + message);
    }

    private void tryEmitNext(final UserServerSentEvent userServerSentEvent) {
        manySinks.emitNext(userServerSentEvent, (signalType, emitResult) -> {

            if (emitResult.isFailure()) {
                log.info("{} Marking emitter {} for removal, due to {}", signalType, getUuid(), emitResult);
                this.errorEmitResult = emitResult;
            }
            if (emitResult.isSuccess()) {
                log.info("{} Succes in sending: {}  to {}", signalType, userServerSentEvent, getUuid());
            }

            return false;
        });
    }

    public void sendPing() {
        LoggingFilter.requestLogMessage(", sendPing " + uuid);
        internalSend("ping", "{ \"uuid\": \"" + uuid + "\"}");
    }

    private void sendPong() {
        LoggingFilter.requestLogMessage(", sending pong " + uuid);
        internalSend("pong", "{ \"uuid\": \"" + uuid + "\"}");
    }

    public void receivePing() {
        LoggingFilter.requestLogMessage(", got ping " + uuid);
        ping = Instant.now();
        sendPong();
    }

    public void receivePong() {
        LoggingFilter.requestLogMessage(", got pong " + uuid);
        pong = Instant.now();
    }

    private void internalSend(final String event) {
        internalSend(event, null);
    }

    private void internalSend(final String event, final Object data) {
        if (log.isTraceEnabled()) {
            log.trace("internalSend() sending event '{}' data: '{}' ", event, data);
        }

        final Instant now = Instant.now();
        final String id = "" + (now.getEpochSecond() * 1000000 + now.getNano());

        tryEmitNext(new UserServerSentEvent(ServerSentEvent.builder().event(event).id(id).data(data == null ? "{}" : data).build()));
    }

    public void sendGameChanged(final Game game) {
        final GameStateEvent playCardEvent = new GameStateEvent(game);
        internalSend("gameStateUpdate", playCardEvent);
    }

    public void sendOneList(final List<String> onlineList) {
        final OnlineListEvent onlineListEvent = new OnlineListEvent();
        onlineListEvent.setOnlineList(onlineList);
        log.info("Sending uuid:{}, userId:{} online friends {}", getUuid(), getUserId(), onlineList);
        internalSend("online", onlineListEvent);
    }

    public void sendUpdateFriends() {
        internalSend("updateFriends");
    }

    public void sendUpdateGames() {
        internalSend("updateGames");
    }

    public void tryEmitComplete() {
        manySinks.tryEmitComplete();
    }

    public Flux<ServerSentEvent<Object>> subscribe() {
        return manySinks.asFlux().map(UserServerSentEvent::getServerSentEvent).publish().autoConnect();
    }
}

