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
    private Instant pingReceived;

    @Getter
    private Instant pongReceived;

    @Getter
    private Instant pingSent;

    @Getter
    private Instant pongSent;

    @Getter
    private Instant cancelled;

    private final Sinks.Many<UserServerSentEvent> unicastSink = Sinks.many().unicast().onBackpressureBuffer();

    public MySseEmitter(final String userIdArg) {
        this.userId = userIdArg;
    }

    public void sendCardServerMessage(final String fromString, final String message) {
        internalSend("cardservermessage", fromString + ": " + message);
    }

    private void tryEmitNext(final UserServerSentEvent userServerSentEvent) {
        final Sinks.EmitResult emitResult = unicastSink.tryEmitNext(userServerSentEvent);
        if (emitResult.isFailure()) {
            unicastSink.tryEmitComplete();
            this.cancelled = Instant.now();
        }
    }

    public void sendPing() {
        LoggingFilter.requestLogMessage(", sendPing " + uuid);
        this.pingSent = Instant.now();
        internalSend("ping", "{ \"uuid\": \"" + uuid + "\"}");
    }

    private void sendPong() {
        LoggingFilter.requestLogMessage(", sending pong " + uuid);
        this.pongSent = Instant.now();
        internalSend("pong", "{ \"uuid\": \"" + uuid + "\"}");
    }

    public void receivePing() {
        LoggingFilter.requestLogMessage(", got ping " + uuid);
        pingReceived = Instant.now();
        sendPong();
    }

    public void receivePong() {
        LoggingFilter.requestLogMessage(", got pong " + uuid);
        pongReceived = Instant.now();
        // log.trace("Ping/pong speed: " + Duration.between(pingSent, pongReceived).toMillis() + " msec");
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
        if (log.isTraceEnabled()) {
            log.trace("Sending uuid:{}, userId:{} online friends {}", getUuid(), getUserId(), onlineList);
        }
        internalSend("online", new OnlineListEvent().onlineList(onlineList));
    }

    public void sendUpdateFriends() {
        internalSend("updateFriends");
    }

    public void sendUpdateGames() {
        internalSend("updateGames");
    }

    public void tryEmitComplete() {
        unicastSink.tryEmitComplete();
    }

    public Flux<ServerSentEvent<Object>> subscribe() {
        return unicastSink.asFlux().map(UserServerSentEvent::getServerSentEvent).publish().autoConnect().doOnCancel(this::cancel);
    }

    public void cancel() {
        this.cancelled = Instant.now();
    }
}

