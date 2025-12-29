package nl.appsource.cardserver.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.BaseEntity;
import nl.appsource.cardserver.service.SseEmitterRepository;
import org.reactivestreams.Publisher;
import org.springframework.context.ApplicationContext;
import org.springframework.data.couchbase.core.mapping.event.ReactiveBeforeConvertCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class BeforeSaveEventGame implements ReactiveBeforeConvertCallback<BaseEntity> {

    private final ApplicationContext applicationContext;

    private SseEmitterRepository sseEmitterRepository;

    @Override
    public Publisher<BaseEntity> onBeforeConvert(final BaseEntity entity, final String collection) {
        entity.setUpdated(Instant.now());
        return Mono.just(entity);
    }

}
