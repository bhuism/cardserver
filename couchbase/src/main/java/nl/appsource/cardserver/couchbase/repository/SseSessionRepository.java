package nl.appsource.cardserver.couchbase.repository;

import nl.appsource.cardserver.model.SseSession;
import org.springframework.data.couchbase.repository.ReactiveCouchbaseRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SseSessionRepository extends ReactiveCouchbaseRepository<SseSession, String>, ReactiveBaseEntityRepository<SseSession> {

//    @ScanConsistency(query = REQUEST_PLUS)
//    Mono<SseSession> findByIdAndCreator(String id, String creator);
//
//    @ScanConsistency(query = REQUEST_PLUS)
//    Mono<Boolean> existsByIdAndCreator(String id, String creator);

//    @Query("UPDATE #{#n1ql.bucket} USE KEYS $id SET updated=NOW_MILLIS(), pingReceived=NOW_MILLIS(), pingReceivedCount=pingReceivedCount+1 RETURNING meta().id")
//    Mono<String> pingReceived(String id);
//
//    @Query("UPDATE #{#n1ql.bucket} USE KEYS $id SET updated=NOW_MILLIS(), pongReceived=NOW_MILLIS(), pongReceivedCount=pongReceivedCount+1 RETURNING meta().id")
//    Mono<String> pongReceived(String id);

}
