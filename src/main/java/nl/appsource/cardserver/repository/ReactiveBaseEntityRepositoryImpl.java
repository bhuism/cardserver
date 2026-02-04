package nl.appsource.cardserver.repository;

import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.java.kv.GetResult;
import com.couchbase.client.java.kv.MutateInSpec;
import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.model.BaseEntity;
import org.springframework.data.couchbase.core.ReactiveCouchbaseTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;

@RequiredArgsConstructor
public class ReactiveBaseEntityRepositoryImpl<T extends BaseEntity> implements ReactiveBaseEntityRepository<T> {

    private final ReactiveCouchbaseTemplate template;

    @Override
    public Mono<String> updateUpdated(final String documentId) {
        return template.getCouchbaseClientFactory()
            .getBucket()
            .defaultCollection()
            .reactive()
            .mutateIn(documentId, Collections.singletonList(MutateInSpec.upsert("updated", System.currentTimeMillis())))
            .map(unused -> documentId)
            .onErrorResume(DocumentNotFoundException.class, ex -> Mono.empty());
    }

    @Override
    public Mono<Long> lock(final String documentId, final Duration duration, final Class<T> clazz) {

        return template.getCouchbaseClientFactory()
            .getBucket()
            .defaultCollection()
            .reactive()
            .getAndLock(documentId, duration)
            .map(GetResult::cas);
    }

    @Override
    public Mono<Void> unLockNoSave(final String documentId, final long cas) {
        return template.getCouchbaseClientFactory()
            .getBucket()
            .defaultCollection()
            .reactive()
            .unlock(documentId, cas);
    }

}
