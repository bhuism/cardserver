package nl.appsource.cardserver.repository;

import nl.appsource.cardserver.model.BaseEntity;
import reactor.core.publisher.Mono;

public interface ReactiveBaseEntityRepository<T extends BaseEntity> {

    Mono<String> updateUpdated(String documentId);

//    @ScanConsistency(query = REQUEST_PLUS)
//    @Query("UPDATE #{#n1ql.bucket} USE KEYS $id SET updated=NOW_MILLIS() RETURNING meta().id")
//    Mono<String> updateUpdated(String id);


}
