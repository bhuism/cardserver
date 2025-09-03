package nl.appsource.cardserver.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.model.Game;
import org.openapitools.model.MessageEvent;
import org.openapitools.model.NewFriendEvent;
import org.openapitools.model.NewGameEvent;
import org.openapitools.model.OnlineListEvent;
import org.openapitools.model.UserMessage;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
public final class MySseEmitter {

    @Getter
    private final String userId;

    @Getter
    private Instant pingReceived;

    @Getter
    private Instant pongReceived;

    @Getter
    private Instant pingSent;

    @Getter
    private Instant pongSent;

    private final Sinks.Many<UserServerSentEvent> unicastSink = Sinks.many().unicast().onBackpressureBuffer();

    public void message(final UserMessage userMessage) {
        internalSend(createServerSentEvent("messageEvent", new MessageEvent().message(userMessage)));
    }

    private void tryEmitNext(final UserServerSentEvent userServerSentEvent) {
        final Sinks.EmitResult emitResult = unicastSink.tryEmitNext(userServerSentEvent);
        if (emitResult.isFailure()) {
            unicastSink.tryEmitComplete();
        }
    }

    public void close() {
        this.unicastSink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
    }

    public void sendPing() {
//        log.info(", sendPing " + uuid);
        this.pingSent = Instant.now();
        internalSend(createPingEvent());
    }

    public UserServerSentEvent createPingEvent() {
        return createServerSentEvent("ping");
    }

    private void sendPong() {
//        log.info(", sending pong " + uuid);
        this.pongSent = Instant.now();
        internalSend(createServerSentEvent("pong"));
    }

    public void receivePing() {
        pingReceived = Instant.now();
        sendPong();
    }

    public void receivePong() {
        pongReceived = Instant.now();
    }

    private void internalSend(final UserServerSentEvent userServerSentEvent) {
        if (log.isTraceEnabled()) {
            log.trace("internalSend() sending event '{}' data: '{}' ", userServerSentEvent.serverSentEvent().event(), userServerSentEvent.serverSentEvent().data());
        }
        tryEmitNext(userServerSentEvent);
    }

    public UserServerSentEvent createServerSentEvent(final String event) {
        return createServerSentEvent(event, null);
    }

    public UserServerSentEvent createServerSentEvent(final String event, final Object data) {
        final Instant now = Instant.now();
        final String id = "" + (now.getEpochSecond() * 1000000 + now.getNano());
        return new UserServerSentEvent(ServerSentEvent.builder().event(event).id(id).data(data == null ? "{}" : data).build());
    }

    public void sendOnlineList(final Flux<String> onlineList) {
        onlineList.collectList().subscribe(list -> internalSend(createServerSentEvent("online", new OnlineListEvent().onlineList(list))));
    }

    public void sendUpdateFriends() {
        internalSend(createServerSentEvent("updateFriends"));
    }

    public void sendUpdateGames() {
        internalSend(createServerSentEvent("updateGames"));
    }

    public void newGame(final Game game) {
        internalSend(createServerSentEvent("newGame", new NewGameEvent().displayNameCreator(game.getCreator()).gameId(game.getId())));
    }

    public void newFriend(final String newFriendId) {
        internalSend(createServerSentEvent("newFriend", new NewFriendEvent().newFriendId(newFriendId)));
    }

    public Flux<ServerSentEvent<Object>> subscribe() {
        return unicastSink.asFlux().map(UserServerSentEvent::serverSentEvent);
    }

    public void sendUpdateGameState(final Game game) {
        log.info("Sending state update for game {}", game.getId());
        internalSend(createServerSentEvent("stateUpdate", game));
    }

}
