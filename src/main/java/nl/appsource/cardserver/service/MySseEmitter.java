package nl.appsource.cardserver.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.model.Boom;
import org.openapitools.model.Game;
import org.openapitools.model.MessageEvent;
import org.openapitools.model.NewFriendEvent;
import org.openapitools.model.NewGameEvent;
import org.openapitools.model.OnlineListEvent;
import org.openapitools.model.User;
import org.openapitools.model.UserMessage;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@Getter
public final class MySseEmitter {

    private final String userId;

    private final Instant created = Instant.now();

    private Instant pingReceived;

    private Instant pongReceived;

    private Instant pingSent;

    private Instant pongSent;

    private int pingReceivedCount = 0;

    private int pongReceivedCount = 0;

    private int pingSentCount = 0;

    private int pongSentCount = 0;

    private Sinks.Many<ServerSentEvent<?>> unicastSink = Sinks.many()
        .unicast()
        .onBackpressureBuffer();

    public void message(final UserMessage userMessage) {
        internalSend(createServerSentEvent("messageEvent", new MessageEvent().message(userMessage)));
    }

    private void emitNext(final ServerSentEvent<?> serverSentEvent) {

        //log.info("tryEmitNext() sending id: {} event '{}'", serverSentEvent.id(), serverSentEvent.event());

        if (unicastSink != null) {
            unicastSink.emitNext(serverSentEvent, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(100)));
        } else {
            log.warn("unicastSink == null");
        }
    }

    public void close() {
        if (unicastSink != null) {
            try {
                this.unicastSink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
            } catch (Throwable t) {
                log.error("", t);
            } finally {
                unicastSink = null;
            }
        }
    }

    private ServerSentEvent<?> createPingEvent() {
        return createServerSentEvent("ping");
    }

    private void sendPong() {
        this.pongSent = Instant.now();
        this.pongSentCount++;
        internalSend(createServerSentEvent("pong"));
    }

    public void receivePing() {
        pingReceived = Instant.now();
        pingReceivedCount++;
        sendPong();
    }

    public void receivePong() {
        pongReceived = Instant.now();
        pongReceivedCount++;
    }

    private <T> void internalSend(final ServerSentEvent<T> serverSentEvent) {
        if (log.isTraceEnabled()) {
            log.trace("internalSend() sending event '{}' data: '{}' ", serverSentEvent.event(), serverSentEvent.data());
        }
        emitNext(serverSentEvent);
    }

    private ServerSentEvent<?> createServerSentEvent(final String event) {
        return createServerSentEvent(event, null);
    }

    private ServerSentEvent<?> createServerSentEvent(final String event, final Object data) {

        final Instant now = Instant.now();
        final String id = "" + (now.getEpochSecond() * 1000000 + now.getNano());

        //log.info("Creating sererSentEvent {} {}", id, event);

        final ServerSentEvent.Builder<Object> builder = ServerSentEvent.builder()
            .event(event)
            .id(id);

        builder.data(Objects.requireNonNullElse(data, "{}"));

        return builder.build();
    }

    public void sendOnlineList(final Flux<String> onlineList) {
        onlineList.collectList()
            .subscribe(list -> internalSend(createServerSentEvent("online", new OnlineListEvent().onlineList(list))));
    }

    public void sendUpdateFriends() {
        internalSend(createServerSentEvent("updateFriends"));
    }

    public void sendUpdateGames() {
        internalSend(createServerSentEvent("updateGames"));
    }

    public void sendUpdateBooms() {
        internalSend(createServerSentEvent("updateBooms"));
    }

    public void newGame(final Game game) {
        internalSend(createServerSentEvent("newGame", new NewGameEvent().displayNameCreator(game.getCreator())
            .gameId(game.getId())));
    }

    public void newFriend(final String newFriendId) {
        internalSend(createServerSentEvent("newFriend", new NewFriendEvent().newFriendId(newFriendId)));
    }

    public Flux<ServerSentEvent<?>> subscribe() {
        this.pingSent = Instant.now();
        return Flux.just(createPingEvent(), createPingEvent(), createPingEvent())
            .mergeWith(unicastSink.asFlux())
            .mergeWith(Flux.interval(Duration.ofSeconds(15))
                .map(aLong -> createPingEvent())
                .doOnNext((_a) -> {
                    this.pingSent = Instant.now();
                    this.pingSentCount++;
                }));
    }

    public void sendUpdateGame(final Game game) {
        internalSend(createServerSentEvent("updateGame", game));
    }

    public void sendUpdateUser(final User user) {
        internalSend(createServerSentEvent("updateUser", user));
    }

    public void sendupdateBoom(final Boom boom) {
        internalSend(createServerSentEvent("updateUser", boom));
    }
}
