package nl.appsource.cardserver.service;

import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.model.SseEvent;
import nl.appsource.cardserver.repository.SseEventRepository;
import nl.appsource.cardserver.utils.IDTYPE;
import org.openapitools.model.MessageEvent;
import org.openapitools.model.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.Map;

import static nl.appsource.cardserver.utils.Utils.idGen;

@Service
@RequiredArgsConstructor
public class SseSenderImpl implements SseSender {

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
        return Flux.fromIterable(userIds).flatMap(userId -> sendUserIdMessage(userId, userMessage)).last();
    }
}
