package nl.appsource.cardserver.repository;

import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.java.ReactiveCollection;
import com.couchbase.client.java.kv.MutateInSpec;
import com.couchbase.client.java.kv.MutationResult;
import com.couchbase.client.java.kv.ReplaceOptions;
import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.model.BaseEntity;
import org.springframework.data.couchbase.core.ReactiveCouchbaseTemplate;
import org.springframework.data.couchbase.core.mapping.CouchbaseDocument;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

@RequiredArgsConstructor
public class ReactiveBaseEntityRepositoryImpl<T extends BaseEntity> implements ReactiveBaseEntityRepository<T> {

    private final ReactiveCouchbaseTemplate template;

    private ReactiveCollection getCollection() {
        return template.getCouchbaseClientFactory().getBucket().reactive().defaultCollection();
    }

    @Override
    public Mono<String> updateUpdated(final String documentId) {
        return getCollection()
            .mutateIn(documentId, Collections.singletonList(MutateInSpec.upsert("updated", System.currentTimeMillis())))
            .map(unused -> documentId)
            .onErrorResume(DocumentNotFoundException.class, ex -> Mono.empty());
    }

    @Override
    public Mono<Map.Entry<T, Long>> lock(final String documentId, final Duration duration, final Class<T> clazz) {

        return getCollection()
            .getAndLock(documentId, duration)
            .map(lockResult -> {

                Map<String, Object> rawJsonMap = lockResult.contentAsObject().toMap();

                // 2. Wrap it in a Spring Data CouchbaseDocument and assign the ID
                CouchbaseDocument sourceDocument = new CouchbaseDocument(documentId);

                sourceDocument.setContent(rawJsonMap); // or sourceDocument.setContent(rawJsonMap)

                // 3. Execute the Spring Data read conversion
                // This automatically parses milliseconds to Instant and injects the @Id
                final T object = template.getConverter().read(clazz, sourceDocument);

                return Map.entry(object, lockResult.cas());

            });

    }

    @Override
    public Mono<MutationResult> updateLocked(final String id, final T document, final long cas) {

        document.setUpdated(Instant.now());

        final CouchbaseDocument target = new CouchbaseDocument(id);

        template.getConverter().write(document, target);

        return getCollection().replace(
            id,
            target.export(),
            ReplaceOptions.replaceOptions().cas(cas)
        );
    }

    @Override
    public Mono<Void> unLockNoSave(final String documentId, final long cas) {
        return getCollection()
            .unlock(documentId, cas);
    }

}
