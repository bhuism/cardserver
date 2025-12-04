package nl.appsource.cardserver.service;

import lombok.Getter;
import lombok.NonNull;
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

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
//@RequiredArgsConstructor
@Getter
public final class MySseEmitter {

    private static final AtomicLong ATOMIC_LONG = new AtomicLong(1);

    public static MyServerSentEvent createMessageEvent(final UUID appIdentifier, final String userId, final UserMessage userMessage) {
        return createServerSentEvent(appIdentifier, userId, "messageEvent", new MessageEvent().message(userMessage));
    }

//    private void emitNext(final ServerSentEvent<?> serverSentEvent) {
//
//        //log.info("tryEmitNext() sending id: {} event '{}'", serverSentEvent.id(), serverSentEvent.event());
//
//        if (unicastSink != null) {
//            unicastSink.emitNext(serverSentEvent, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(100)));
//        } else {
//            log.warn("unicastSink == null");
//        }
//    }
//
//    public void close() {
//        if (unicastSink != null) {
//            try {
//                final Sinks.EmitResult emitResult = this.unicastSink.tryEmitComplete();
//
//                if (emitResult.isFailure()) {
//                    log.info("close() failure");
//                }
//
//            } catch (Throwable t) {
//                log.error("", t);
//            } finally {
//                unicastSink = null;
//            }
//        }
//    }

    public static MyServerSentEvent createPingEvent(final Long counter) {
        return createServerSentEvent(null, null, "ping");
    }

    public static MyServerSentEvent createPongEvent(final UUID appIdentifier, final String userId) {
        return createServerSentEvent(appIdentifier, userId, "pong");
    }

//    private void sendPong() {
//        this.pongSent = Instant.now();
//        this.pongSentCount++;
//        internalSend(createServerSentEvent("pong"));
//    }
//
//    public void receivePing() {
//        pingReceived = Instant.now();
//        pingReceivedCount++;
//        sendPong();
//    }
//
//    public void receivePong() {
//        pongReceived = Instant.now();
//        pongReceivedCount++;
//    }

//    private <T> void internalSend(final ServerSentEvent<T> serverSentEvent) {
//        if (log.isTraceEnabled()) {
//            log.trace("internalSend() sending event '{}' data: '{}' ", serverSentEvent.event(), serverSentEvent.data());
//        }
//        emitNext(serverSentEvent);
//    }

    private static MyServerSentEvent createServerSentEvent(final UUID appIdentifier, final String userId, final String event) {
        return createServerSentEvent(appIdentifier, userId, event, null);
    }

    public static MyServerSentEvent createServerSentEvent(final UUID appIdentifier, final String userId, final User user) {
        return createServerSentEvent(appIdentifier, userId, "updateUser", user);
    }

    public static MyServerSentEvent createServerSentEvent(final UUID appIdentifier, final String userId, final Game game) {
        return createServerSentEvent(appIdentifier, userId, "updateGame", game);
    }

    public static MyServerSentEvent createServerSentEvent(final UUID appIdentifier, final String userId, final Boom boom) {
        return createServerSentEvent(appIdentifier, userId, "updateBoom", boom);
    }

    public static MyServerSentEvent createServerSentEvent(final UUID appIdentifier, final String userId, final String event, final Object data) {

        final Instant now = Instant.now();
        final Long id = ATOMIC_LONG.getAndIncrement();

        //log.info("Creating sererSentEvent {} {}", id, event);

        final ServerSentEvent.Builder<@NonNull Object> builder = ServerSentEvent.builder().event(event).id("id:" + id);

        builder.data(Objects.requireNonNullElse(data, "{}"));

        return new MyServerSentEvent() {

            @Override
            public UUID getAppIdentifier() {
                return appIdentifier;
            }

            @Override
            public String getUserId() {
                return userId;
            }

            @Override
            public ServerSentEvent<@NonNull Object> getServerSentEvent() {
                return builder.build();
            }
        };
    }

    public static MyServerSentEvent createOnlineList(final UUID appIdentifier, final String userId, final List<String> onlineList) {
        return createServerSentEvent(appIdentifier, userId, "online", new OnlineListEvent().onlineList(onlineList));
    }

    public static MyServerSentEvent createUpdateFriends(final UUID appIdentifier, final String userId) {
        return createServerSentEvent(appIdentifier, userId, "updateFriends");
    }

    public static MyServerSentEvent createUpdateGames(final UUID appIdentifier, final String userId) {
        return createServerSentEvent(appIdentifier, userId, "updateGames");
    }

    public static MyServerSentEvent createUpdateBooms(final UUID appIdentifier, final String userId) {
        return createServerSentEvent(appIdentifier, userId, "updateBooms");
    }

    public static MyServerSentEvent createNewGame(final UUID appIdentifier, final String userId, final Game game) {
        return createServerSentEvent(appIdentifier, userId, "newGame", new NewGameEvent().displayNameCreator(game.getCreator()).gameId(game.getId()));
    }

    public static MyServerSentEvent newFriend(final UUID appIdentifier, final String userId, final String newFriendId) {
        return createServerSentEvent(appIdentifier, userId, "newFriend", new NewFriendEvent().newFriendId(newFriendId));
    }

//    public Flux<ServerSentEvent<?>> subscribe() {
//        this.pingSent = Instant.now();
//        return Flux.just(createPingEvent(), createPingEvent(), createPingEvent()).mergeWith(unicastSink.asFlux())
//            .mergeWith(Flux.interval(Duration.ofSeconds(15))
//                .map(aLong -> createPingEvent()).doOnNext((_a) -> {
//            this.pingSent = Instant.now();
//            this.pingSentCount++;
//        }));
//    }

//    public void sendUpdateGame(final Game game) {
//        internalSend(createServerSentEvent(game));
//    }
//
//    public void sendUpdateUser(final User user) {
//        internalSend(createServerSentEvent(user));
//    }
//
//    public static void sendupdateBoom(final Boom boom) {
//        internalSend(createServerSentEvent(boom));
//    }

//    public Disposable sendFlux(final Flux<ServerSentEvent<?>> flux) {
//        return flux.subscribe(this::emitNext);
//    }

}
