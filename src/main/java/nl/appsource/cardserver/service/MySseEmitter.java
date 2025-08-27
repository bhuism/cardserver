package nl.appsource.cardserver.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.model.Game;
import org.openapitools.model.MessageEvent;
import org.openapitools.model.NewFriendEvent;
import org.openapitools.model.NewGameEvent;
import org.openapitools.model.OnlineListEvent;
import org.openapitools.model.PingEvent;
import org.openapitools.model.PongEvent;
import org.openapitools.model.UserMessage;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;
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

    public void message(final UserMessage userMessage) {
        internalSend(createServerSentEvent("messageEvent", new MessageEvent().message(userMessage)));
    }

    private void tryEmitNext(final UserServerSentEvent userServerSentEvent) {
        final Sinks.EmitResult emitResult = unicastSink.tryEmitNext(userServerSentEvent);
        if (emitResult.isFailure()) {
            unicastSink.tryEmitComplete();
            this.cancelled = Instant.now();
        }
    }

    public void sendPing() {
//        log.info(", sendPing " + uuid);
        this.pingSent = Instant.now();
        internalSend(createPingEvent());
    }

    public UserServerSentEvent createPingEvent() {
        return createServerSentEvent("ping", new PingEvent().uuid(uuid));
    }

    private void sendPong() {
//        log.info(", sending pong " + uuid);
        this.pongSent = Instant.now();
        internalSend(createServerSentEvent("pong", new PongEvent().uuid(uuid)));
    }

    public void receivePing() {
//        log.info(", got ping " + uuid);
        pingReceived = Instant.now();
        sendPong();
    }

    public void receivePong() {
//        log.info(", got pong " + uuid);
        pongReceived = Instant.now();
        // log.trace("Ping/pong speed: " + Duration.between(pingSent, pongReceived).toMillis() + " msec");
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
        if (log.isTraceEnabled()) {
            log.trace("Sending uuid:{}, userId:{} online friends {}", getUuid(), getUserId(), onlineList);
        }
        onlineList.collectList().subscribe(list ->
            internalSend(createServerSentEvent("online", new OnlineListEvent().onlineList(list)))
        );
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

    public void tryEmitComplete() {
        unicastSink.tryEmitComplete();
    }

    public Flux<ServerSentEvent<Object>> subscribe() {
        return unicastSink.asFlux()
            .map(UserServerSentEvent::serverSentEvent)
            .doOnNext(objectServerSentEvent -> {
                if ("message".equals(objectServerSentEvent.event())) {
                    final UserMessage userMessage = (UserMessage) objectServerSentEvent.data();
                    if (userMessage == null || !userMessage.getUserId().equals(userId)) {
                        log.warn("Message for wrong user");
                    }
                }
            })
            .share()
            .doOnCancel(this::cancel);
    }

    public void cancel() {
        this.cancelled = Instant.now();
    }
}

