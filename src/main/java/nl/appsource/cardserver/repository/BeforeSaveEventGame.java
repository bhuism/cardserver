package nl.appsource.cardserver.repository;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.BaseEntity;
import org.reactivestreams.Publisher;
import org.springframework.data.couchbase.core.mapping.event.ReactiveBeforeConvertCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Component
public class BeforeSaveEventGame implements ReactiveBeforeConvertCallback<BaseEntity> {
    @Override
    public Publisher<BaseEntity> onBeforeConvert(final BaseEntity entity, final String collection) {
        log.info("Saving entity {} with id {}", entity.getClass().getSimpleName(), entity.getId());
        entity.setUpdated(Instant.now());
        return Mono.just(entity);
    }
}
