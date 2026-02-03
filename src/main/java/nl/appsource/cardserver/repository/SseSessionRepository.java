package nl.appsource.cardserver.repository;

import nl.appsource.cardserver.model.SseSession;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.data.couchbase.repository.ReactiveCouchbaseRepository;
import org.springframework.data.couchbase.repository.ScanConsistency;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import static com.couchbase.client.java.query.QueryScanConsistency.REQUEST_PLUS;

@Repository
public interface SseSessionRepository extends ReactiveCouchbaseRepository<SseSession, String>, ReactiveBaseEntityRepository<SseSession> {

    @ScanConsistency(query = REQUEST_PLUS)
    Mono<SseSession> findByIdAndCreator(String id, String creator);

    @ScanConsistency(query = REQUEST_PLUS)
    Mono<Boolean> existsByIdAndCreator(String id, String creator);

    @Query("UPDATE #{#n1ql.bucket} USE KEYS $id SET updated=NOW_MILLIS(), pingReceived=NOW_MILLIS(), pingReceivedCount=pingReceivedCount+1 RETURNING meta().id")
    Mono<String> ping(String id);

    @Query("UPDATE #{#n1ql.bucket} USE KEYS $id SET updated=NOW_MILLIS(), pongReceived=NOW_MILLIS(), pongReceivedCount=pongReceivedCount+1 RETURNING meta().id")
    Mono<String> pong(String id);

}
