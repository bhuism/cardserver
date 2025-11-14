package nl.appsource.cardserver.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.BaseEntity;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.service.SseEmitterRepository;
import org.reactivestreams.Publisher;
import org.springframework.data.couchbase.core.mapping.CouchbaseDocument;
import org.springframework.data.couchbase.core.mapping.event.ReactiveAfterConvertCallback;
import org.springframework.data.couchbase.core.mapping.event.ReactiveBeforeConvertCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class BeforeSaveEventGame implements ReactiveBeforeConvertCallback<BaseEntity>, ReactiveAfterConvertCallback<BaseEntity> {

    private final SseEmitterRepository sseEmitterRepository;

    @Override
    public Publisher<BaseEntity> onBeforeConvert(final BaseEntity entity, final String collection) {
        log.info("Saving entity {} with id {}", entity.getClass().getSimpleName(), entity.getId());
        entity.setUpdated(Instant.now());
        return Mono.just(entity);
    }

    @Override
    public Publisher<BaseEntity> onAfterConvert(final BaseEntity entity, final CouchbaseDocument document, String collection) {
        if (entity instanceof Game) {
            sseEmitterRepository.updateGameState((Game)entity);
        }
        return Mono.just(entity);
    }
}
