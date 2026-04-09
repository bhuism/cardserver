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

import java.util.Set;

import static nl.appsource.cardserver.openapi.MyServerSentEvent.messageEvent;
import static nl.appsource.cardserver.openapi.MyServerSentEvent.onlineList;
import static nl.appsource.cardserver.utils.Utils.isAiPlayer;

@RequiredArgsConstructor
public class SseEventSenderImpl implements SseEventSender {

    private final RedisPubSubService redisPubSubService;

    @Override
    public Mono<Void> sendUserIdMessage(final String to, final String from, final String message, final UserMessage.VariantEnum variant) {
        final MessageEvent messageEvent = new MessageEvent().message(new UserMessage().userId(from).message(message).variant(variant));
        return redisPubSubService.broadCast(to, messageEvent(messageEvent)).then();
    }

    @Override
    public Mono<Void> sendUserIdsMessage(final Set<String> to, final String from, final String message, final UserMessage.VariantEnum variant) {
        final MessageEvent messageEvent = new MessageEvent().message(new UserMessage().userId(from).message(message).variant(variant));
        return redisPubSubService.broadCast(Flux.fromIterable(to), messageEvent(messageEvent));
    }

    @Override
    public Mono<Void> boomsChanged(final Set<String> userIdWithAi) {
        return redisPubSubService.broadCast(Flux.fromIterable(userIdWithAi).filter(userId -> !isAiPlayer(userId)), updateBooms());
    }

    @Override
    public Mono<Void> gamesChanged(final Set<String> userIds) {
        return redisPubSubService.broadCast(Flux.fromIterable(userIds), updateGames());
    }

    @Override
    public Mono<Void> friendsChanged(final Set<String> userIds) {
        return redisPubSubService.broadCast(Flux.fromIterable(userIds), updateFriends());
    }

    @Override
    public Mono<Void> newGame(final Game game) {

        final Flux<String> topics = Flux.fromIterable(game.getPlayers()).filter(userId -> !isAiPlayer(userId) && !userId.equals(game.getCreator()));
        final NewGameEvent newGameEvent = new NewGameEvent().creator(game.getCreator()).gameId(game.getId());

        return redisPubSubService.broadCast(topics, newGame(newGameEvent));

    }

    @Override
    public Mono<Void> sendOnlineListTo(final String userId, final Flux<String> onlineListFlux) {
        return onlineListFlux.collectList().map(onlineList -> new OnlineListEvent().onlineList(onlineList))
            .flatMap(onlineListEvent1 -> redisPubSubService.broadCast(userId, onlineList(onlineListEvent1)))
            .then();
    }

    public static MyServerSentEvent newGame(final NewGameEvent newGameEvent) {
        return new MyServerSentEvent("newGame", newGameEvent);
    }

    public static MyServerSentEvent updateBooms() {
        return new MyServerSentEvent("updateBooms");
    }

    public static MyServerSentEvent updateGames() {
        return new MyServerSentEvent("updateGames");
    }

    public static MyServerSentEvent updateFriends() {
        return new MyServerSentEvent("updateFriends");
    }

}
