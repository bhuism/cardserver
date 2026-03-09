package nl.appsource.cardserver.service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import nl.appsource.cardserver.openapi.service.RedisPubSubService;
import nl.appsource.generated.openapi.model.MessageEvent;
import nl.appsource.generated.openapi.model.NewGameEvent;
import nl.appsource.generated.openapi.model.OnlineListEvent;
import nl.appsource.generated.openapi.model.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

import static nl.appsource.cardserver.couchbase.utils.GameEngineImpl.isAiPlayer;
import static nl.appsource.cardserver.openapi.MyServerSentEvent.messageEvent;
import static nl.appsource.cardserver.openapi.MyServerSentEvent.onlineList;
import static nl.appsource.cardserver.openapi.MyServerSentEvent.updateFriends;
import static nl.appsource.cardserver.openapi.MyServerSentEvent.updateGames;

@Service
@RequiredArgsConstructor
public class SseEventSenderImpl implements SseEventSender {

    private final RedisPubSubService redisPubSubService;

    @Override
    public Mono<Void> sendUserIdMessage(final String to, final String from, final String message, final UserMessage.VariantEnum variant) {
        final MessageEvent messageEvent = new MessageEvent().message(new UserMessage().userId(from).message(message).variant(variant));
        return redisPubSubService.publish(to, messageEvent(messageEvent)).then();
    }

    @Override
    public Mono<Void> sendUserIdsMessage(final Set<String> to, final String from, final String message, final UserMessage.VariantEnum variant) {
        final MessageEvent messageEvent = new MessageEvent().message(new UserMessage().userId(from).message(message).variant(variant));
        return redisPubSubService.publish(Flux.fromIterable(to), messageEvent(messageEvent));
    }

    @Override
    public Mono<Void> boomsChanged(final Set<String> userIdWithAi) {
        return redisPubSubService.publish(Flux.fromIterable(userIdWithAi).filter(userId -> !isAiPlayer(userId)), MyServerSentEvent.updateBooms());
    }

    @Override
    public Mono<Void> gamesChanged(final Set<String> userIds) {
        return redisPubSubService.publish(Flux.fromIterable(userIds), updateGames());
    }

    @Override
    public Mono<Void> friendsChanged(final Set<String> userIds) {
        return redisPubSubService.publish(Flux.fromIterable(userIds), updateFriends());
    }

    @Override
    public Mono<Void> newGame(final Game game) {

        final Flux<String> topics = Flux.fromIterable(game.getPlayers()).filter(userId -> !isAiPlayer(userId) && !userId.equals(game.getCreator()));
        final NewGameEvent newGameEvent = new NewGameEvent().creator(game.getCreator()).gameId(game.getId());

        return redisPubSubService.publish(topics, MyServerSentEvent.newGame(newGameEvent));

    }

    @Override
    public Mono<Void> sendOnlineListTo(final String userId, final Set<@NonNull String> onlineList) {
        final OnlineListEvent onlineListEvent = new OnlineListEvent().onlineList(onlineList.stream().toList());
        return redisPubSubService.publish(userId, onlineList(onlineListEvent)).then();
    }

}
