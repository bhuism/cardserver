package nl.appsource.cardserver.service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.couchbase.model.Game;
import nl.appsource.cardserver.couchbase.model.SseEvent;
import nl.appsource.cardserver.couchbase.repository.SseEventRepository;
import nl.appsource.cardserver.couchbase.repository.UserRepository;
import nl.appsource.cardserver.utils.IDTYPE;
import nl.appsource.generated.openapi.model.MessageEvent;
import nl.appsource.generated.openapi.model.NewGameEvent;
import nl.appsource.generated.openapi.model.OnlineListEvent;
import nl.appsource.generated.openapi.model.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static nl.appsource.cardserver.service.GameEngineImpl.isAiPlayer;
import static nl.appsource.cardserver.utils.Utils.idGen;

@Service
@RequiredArgsConstructor
public class SseEventSenderImpl implements SseEventSender {

    private final SseEventRepository sseEventRepository;

    private final JsonMapper jsonMapper;

    private final UserRepository userRepository;

//    @Override
//    public Mono<Void> sendAppIdentifierMessage(final String appIdentifier, final String userId, final String message, final UserMessage.VariantEnum variant) {
//        return Mono.just(new SseEvent(idGen(IDTYPE.EVNT, 16), appIdentifier, null, "messageEvent", jsonMapper.convertValue(new MessageEvent().message(new UserMessage().userId(userId).message(message).variant(variant)), Map.class)))
//            .flatMap(sseEventRepository::save)
//            .then();
//    }

    @Override
    public Mono<Void> sendUserIdMessage(final String userId, final String message, final UserMessage.VariantEnum variant) {
        return Mono.just(new SseEvent(idGen(IDTYPE.EVNT, 16), null, userId, "messageEvent", jsonMapper.convertValue(MessageEvent.builder().message(UserMessage.builder().userId(userId).message(message).variant(variant).build()), Map.class)))
            .flatMap(sseEventRepository::save)
            .then();
    }

    @Override
    public Mono<Void> sendUserIdsMessage(final Set<String> userIds, final String message, final UserMessage.VariantEnum variant) {
        return Flux.fromIterable(userIds).flatMap(userId -> sendUserIdMessage(userId, message, variant)).then();
    }

    @Override
    public Mono<Void> sendPong(final String appIdentifier) {
        return Mono.just(new SseEvent(idGen(IDTYPE.EVNT, 16), appIdentifier, null, "pong", null))
            .flatMap(sseEventRepository::save)
            .then();
    }

    @Override
    public Mono<Void> boomsChanged(final Set<String> userIds) {
        return Flux.fromIterable(userIds)
            .filter(userId -> !isAiPlayer(userId))
            .map(userId -> new SseEvent(idGen(IDTYPE.EVNT, 16), null, userId, "updateBooms", null))
            .flatMap(sseEventRepository::save)
            .then();
    }

    @Override
    public Mono<Void> gamesChanged(final Set<String> userIds) {
        return Flux.fromIterable(userIds)
            .filter(userId -> !isAiPlayer(userId))
            .map(userId -> new SseEvent(idGen(IDTYPE.EVNT, 16), null, userId, "updateGames", null))
            .flatMap(sseEventRepository::save)
            .then();
    }

    @Override
    public Mono<Void> friendsChanged(final Set<String> userIds) {
        return Flux.fromIterable(userIds)
            .filter(userId -> !isAiPlayer(userId))
            .map(userId -> new SseEvent(idGen(IDTYPE.EVNT, 16), null, userId, "updateFriends", null))
            .flatMap(sseEventRepository::save)
            .then();
    }

    @Override
    public Mono<Void> newGame(final Game game) {
        return Flux.fromIterable(game.getPlayers())
            .filter(userId -> !isAiPlayer(userId))
            .filter(player -> !player.equals(game.getCreator()))
            .flatMap(player -> {
                return Mono.just(new SseEvent(idGen(IDTYPE.EVNT, 16), null, player, "newGame", jsonMapper.convertValue(NewGameEvent.builder().displayNameCreator(game.getCreator()).gameId(game.getId()).build(), Map.class)))
                    .flatMap(sseEventRepository::save)
                    .then();
            })
            .then();
    }

    @Override
    public Mono<Void> sendOnlineListTo(final String userId, final Set<@NonNull String> onlineList) {
        return Mono.just(new SseEvent(idGen(IDTYPE.EVNT, 16), null, userId, "onlineList", jsonMapper.convertValue(OnlineListEvent.builder().onlineList(onlineList.stream().toList()).build(), Map.class)))
            .flatMap(sseEventRepository::save)
            .then();
    }

    @Override
    public Mono<Void> sendOnlineListToFriendsOf(final String userId) {
        return userRepository.getOnlineFriends(userId).flatMap(this::sendOnlineListTo).then();
    }

    @Override
    public Mono<Void> sendOnlineListTo(final String userId) {
        return userRepository.getOnlineFriends(userId)
            .collect(Collectors.toSet())
            .flatMap(onlineList -> sendOnlineListTo(userId, onlineList));
    }
}
