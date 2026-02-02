package nl.appsource.cardserver.repository;

import nl.appsource.cardserver.model.SseSession;
import org.springframework.data.couchbase.repository.ReactiveCouchbaseRepository;
import org.springframework.data.couchbase.repository.ScanConsistency;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import static com.couchbase.client.java.query.QueryScanConsistency.REQUEST_PLUS;

@Repository
public interface SseSessionRepository extends ReactiveCouchbaseRepository<SseSession, String> {

    @ScanConsistency(query = REQUEST_PLUS)
    Mono<SseSession> findByIdAndCreator(String id, String creator);

    @ScanConsistency(query = REQUEST_PLUS)
    Mono<Boolean> existsByIdAndCreator(String id, String creator);

}
