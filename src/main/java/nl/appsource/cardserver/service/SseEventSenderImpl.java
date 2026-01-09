package nl.appsource.cardserver.service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.model.SseEvent;
import nl.appsource.cardserver.repository.SseEventRepository;
import nl.appsource.cardserver.utils.IDTYPE;
import org.openapitools.model.MessageEvent;
import org.openapitools.model.OnlineListEvent;
import org.openapitools.model.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static nl.appsource.cardserver.utils.Utils.idGen;

@Service
@RequiredArgsConstructor
public class SseEventSenderImpl implements SseEventSender {

    private final SseEventRepository sseEventRepository;

    private final JsonMapper jsonMapper;

    @Override
    public Mono<Void> sendAppIdentifierMessage(final String appIdentifier, final UserMessage userMessage) {
        return Mono.just(new SseEvent(idGen(IDTYPE.EVNT, 16), appIdentifier, null, "messageEvent", jsonMapper.convertValue(new MessageEvent().message(userMessage), Map.class)))
            .flatMap(sseEventRepository::save)
            .then();
    }

    @Override
    public Mono<Void> sendUserIdMessage(final String userId, final UserMessage userMessage) {
        return Mono.just(new SseEvent(idGen(IDTYPE.EVNT, 16), null, userId, "messageEvent", jsonMapper.convertValue(new MessageEvent().message(userMessage), Map.class)))
            .flatMap(sseEventRepository::save)
            .then();
    }

    @Override
    public Mono<Void> sendUserIdMessage(final Collection<String> userIds, final UserMessage userMessage) {
        return Flux.fromIterable(userIds).flatMap(userId -> sendUserIdMessage(userId, userMessage)).then();
    }

    @Override
    public Mono<Void> sendPong(final String appIdentifier) {
        return Mono.just(new SseEvent(idGen(IDTYPE.EVNT, 16), appIdentifier, null, "pong", null))
            .flatMap(sseEventRepository::save)
            .then();
    }

    @Override
    public Mono<Void> boomsChanged(final Collection<String> userIds) {
        return Flux.fromIterable(userIds)
            .map(userId -> new SseEvent(idGen(IDTYPE.EVNT, 16), null, userId, "updateBooms", null))
            .flatMap(sseEventRepository::save)
            .then();
    }

    @Override
    public Mono<Void> gamesChanged(final Collection<String> userIds) {
        return Flux.fromIterable(userIds)
            .map(userId -> new SseEvent(idGen(IDTYPE.EVNT, 16), null, userId, "updateGames", null))
            .flatMap(sseEventRepository::save)
            .then();
    }

    @Override
    public Mono<Void> friendsChanged(final Collection<String> userIds) {
        return Flux.fromIterable(userIds)
            .map(userId -> new SseEvent(idGen(IDTYPE.EVNT, 16), null, userId, "updateFriends", null))
            .flatMap(sseEventRepository::save)
            .then();
    }

    @Override
    public Mono<Void> sendOnlineListTo(final String userId, final List<@NonNull String> onlineList) {
        return Mono.just(new SseEvent(idGen(IDTYPE.EVNT, 16), null, userId, "onlineList", jsonMapper.convertValue(new OnlineListEvent().onlineList(onlineList), Map.class)))
            .flatMap(sseEventRepository::save)
            .then();

    }
}
