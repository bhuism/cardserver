package nl.appsource.cardserver.service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import nl.appsource.cardserver.openapi.service.RedisPublisher;
import nl.appsource.generated.openapi.model.MessageEvent;
import nl.appsource.generated.openapi.model.NewGameEvent;
import nl.appsource.generated.openapi.model.OnlineListEvent;
import nl.appsource.generated.openapi.model.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

import static nl.appsource.cardserver.couchbase.utils.GameEngineImpl.isAiPlayer;

@Service
@RequiredArgsConstructor
public class SseEventSenderImpl implements SseEventSender {

    private final RedisPublisher redisPublisher;

    private final JsonMapper jsonMapper;

    @Override
    public Mono<Void> sendUserIdMessage(final String to, final String from, final String message, final UserMessage.VariantEnum variant) {
        final MessageEvent messageEvent = MessageEvent.builder().message(UserMessage.builder().userId(from).message(message).variant(variant).build()).build();
        return redisPublisher.publish(to, jsonMapper.writeValueAsString(MyServerSentEvent.messageEvent(messageEvent))).then();
    }

    @Override
    public Mono<Void> sendUserIdsMessage(final Set<String> to, final String from, final String message, final UserMessage.VariantEnum variant) {
        final MessageEvent messageEvent = MessageEvent.builder().message(UserMessage.builder().userId(from).message(message).variant(variant).build()).build();
        return redisPublisher.publish(Flux.fromIterable(to), jsonMapper.writeValueAsString(MyServerSentEvent.messageEvent(messageEvent)));
    }

    @Override
    public Mono<Void> boomsChanged(final Set<String> userIdWithAi) {
        return redisPublisher.publish(Flux.fromIterable(userIdWithAi).filter(userId -> !isAiPlayer(userId)), jsonMapper.writeValueAsString(MyServerSentEvent.updateBooms()));
    }

    @Override
    public Mono<Void> gamesChanged(final Set<String> userIds) {
        return redisPublisher.publish(Flux.fromIterable(userIds), jsonMapper.writeValueAsString(MyServerSentEvent.updateGames()));
    }

    @Override
    public Mono<Void> friendsChanged(final Set<String> userIds) {
        return redisPublisher.publish(Flux.fromIterable(userIds), jsonMapper.writeValueAsString(MyServerSentEvent.updateFriends()));
    }

    @Override
    public Mono<Void> newGame(final Game game) {

        final Flux<String> topics = Flux.fromIterable(game.getPlayers())
            .filter(userId -> !isAiPlayer(userId))
            .filter(player -> !player.equals(game.getCreator()));

        final NewGameEvent newGameEvent = NewGameEvent.builder().displayNameCreator(game.getCreator()).gameId(game.getId()).build();
        return redisPublisher.publish(topics, jsonMapper.writeValueAsString(MyServerSentEvent.newGame(newGameEvent)));

    }

    @Override
    public Mono<Void> sendOnlineListTo(final String userId, final Set<@NonNull String> onlineList) {
        final OnlineListEvent onlineListEvent = OnlineListEvent.builder().onlineList(onlineList.stream().toList()).build();
        return redisPublisher.publish(userId, jsonMapper.writeValueAsString(MyServerSentEvent.onlineList(onlineListEvent))).then();
    }

}
