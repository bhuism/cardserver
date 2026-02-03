package nl.appsource.cardserver.repository;

import nl.appsource.cardserver.model.BaseEntity;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.data.couchbase.repository.ScanConsistency;
import reactor.core.publisher.Mono;

import static com.couchbase.client.java.query.QueryScanConsistency.REQUEST_PLUS;

public interface ReactiveBaseEntityRepository<T extends BaseEntity> {

    @ScanConsistency(query = REQUEST_PLUS)
    @Query("UPDATE #{#n1ql.bucket} USE KEYS $id SET updated=NOW_MILLIS() RETURNING meta().id")
    Mono<String> updateUpdated(String id);

}
