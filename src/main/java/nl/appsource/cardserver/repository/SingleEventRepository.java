package nl.appsource.cardserver.repository;

import nl.appsource.cardserver.model.SingleEvent;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.data.couchbase.repository.ReactiveCouchbaseRepository;
import org.springframework.data.couchbase.repository.ScanConsistency;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import static com.couchbase.client.java.query.QueryScanConsistency.REQUEST_PLUS;

@Repository
public interface SingleEventRepository extends ReactiveCouchbaseRepository<SingleEvent, String> {

    @Query(value = "UPDATE #{#n1ql.bucket} USE KEYS $id SET lockedBy = $lockedBy WHERE META().id==$id AND lockedBy IS MISSING AND handledBy IS MISSING", readonly = false)
    @ScanConsistency(query = REQUEST_PLUS)
    Mono<Void> lockById(String id, String lockedBy);

    @Query(value = "UPDATE #{#n1ql.bucket} USE KEYS $id SET handledBy = $handledBy WHERE META().id==$id AND lockedBy=$handledBy AND handledBy IS MISSING", readonly = false)
    @ScanConsistency(query = REQUEST_PLUS)
    Mono<Void> handledBy(String id, String handledBy);

}
