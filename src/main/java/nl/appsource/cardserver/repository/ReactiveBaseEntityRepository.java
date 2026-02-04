package nl.appsource.cardserver.repository;

import nl.appsource.cardserver.model.BaseEntity;
import reactor.core.publisher.Mono;

import java.time.Duration;

public interface ReactiveBaseEntityRepository<T extends BaseEntity> {

    Mono<String> updateUpdated(String documentId);

    Mono<Long> lock(String documentId, Duration duration, Class<T> clazz);

//    Mono<Void> unLockSave(T document);

    Mono<Void> unLockNoSave(String documentId, long cas);

//    @ScanConsistency(query = REQUEST_PLUS)
//    @Query("UPDATE #{#n1ql.bucket} USE KEYS $id SET updated=NOW_MILLIS() RETURNING meta().id")
//    Mono<String> updateUpdated(String id);


}
