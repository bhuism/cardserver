package nl.appsource.cardserver.repository;

import nl.appsource.cardserver.model.BaseEntity;
import org.springframework.data.couchbase.repository.Query;
import reactor.core.publisher.Mono;

public interface ReactiveBaseEntityRepository<T extends BaseEntity> {

    @Query("UPDATE #{#n1ql.bucket} USE KEYS $id SET updated=NOW_MILLIS() RETURNING meta().id")
    Mono<String> updateUpdated(String id);

}
