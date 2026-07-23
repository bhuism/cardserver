package nl.appsource.cardserver.openapi.service;

import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import nl.appsource.generated.openapi.model.Game;
import nl.appsource.generated.openapi.model.MessageEvent;
import nl.appsource.generated.openapi.model.NewGameEvent;
import nl.appsource.generated.openapi.model.OnlineListEvent;
import nl.appsource.generated.openapi.model.UserMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Set;

import static nl.appsource.cardserver.openapi.MyServerSentEvent.messageEvent;
import static nl.appsource.cardserver.openapi.MyServerSentEvent.onlineList;

@RequiredArgsConstructor
public class SseEventSenderImpl implements SseEventSender {

    private final KafkaSender kafkaSender;

    @Override
    public Mono<Void> sendUserIdMessage(final String to, final String from, final String message, final UserMessage.VariantEnum variant) {
        final MessageEvent messageEvent = new MessageEvent().message(new UserMessage().userId(from).message(message).variant(variant));
        kafkaSender.sendSse(messageEvent(messageEvent, Set.of(to)));
        return Mono.empty();
    }

    @Override
    public Mono<Void> sendUserIdsMessage(final Set<String> to, final String from, final String message, final UserMessage.VariantEnum variant) {
        final MessageEvent messageEvent = new MessageEvent().message(new UserMessage().userId(from).message(message).variant(variant));
        kafkaSender.sendSse(messageEvent(messageEvent, to));
        return Mono.empty();
    }

    @Override
    public Mono<Void> boomsChanged(final Set<String> userId) {
        kafkaSender.sendSse(updateBooms(userId));
        return Mono.empty();
    }

    @Override
    public Mono<Void> gamesChanged(final Set<String> userIds) {
        kafkaSender.sendSse(updateGames(userIds));
        return Mono.empty();
    }

    @Override
    public Mono<Void> friendsChanged(final Set<String> userIds) {
        kafkaSender.sendSse(updateFriends(userIds));
        return Mono.empty();
    }

    @Override
    public Mono<Void> newGame(final Game game) {

//        final Flux<String> topics = Flux.fromIterable(game.getPlayers()).filter(userId -> !isAiPlayer(userId) && !userId.equals(game.getCreator()));
        final NewGameEvent newGameEvent = new NewGameEvent().creator(game.getCreator()).gameId(game.getId());

        kafkaSender.sendSse(newGame(newGameEvent, new HashSet<>(game.getPlayers())));
        return Mono.empty();
    }

    @Override
    public Mono<Void> sendOnlineListTo(final String userId, final Flux<String> onlineListFlux) {
        return onlineListFlux.collectList().map(onlineList -> new OnlineListEvent().onlineList(onlineList))
            .doOnNext(onlineListEvent1 -> kafkaSender.sendSse(onlineList(onlineListEvent1, Set.of(userId))))
            .then();
    }

    public static MyServerSentEvent<Void> newGame(final NewGameEvent newGameEvent, final Set<String> userIds) {
        return new MyServerSentEvent<>("newGame", userIds);
    }

    public static MyServerSentEvent<Void> updateBooms(final Set<String> userIds) {
        return new MyServerSentEvent<>("updateBooms", userIds);
    }

    public static MyServerSentEvent<Void> updateGames(final Set<String> userIds) {
        return new MyServerSentEvent<>("updateGames", userIds);
    }

    public static MyServerSentEvent<Void> updateFriends(final Set<String> userIds) {
        return new MyServerSentEvent<>("updateFriends", userIds);
    }

}
