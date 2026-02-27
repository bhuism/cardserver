package nl.appsource.cardserver.couchbase.repository;

import com.couchbase.client.java.kv.MutationResult;
import nl.appsource.cardserver.couchbase.model.BaseEntity;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

public interface ReactiveBaseEntityRepository<T extends BaseEntity> {

    Mono<String> updateUpdated(String documentId);

    Mono<Map.Entry<T, Long>> lock(String documentId, Duration duration, Class<T> clazz);

    Mono<MutationResult> updateLocked(String id, T document, long cas);

    Mono<Void> unLockNoSave(String documentId, long cas);

}
