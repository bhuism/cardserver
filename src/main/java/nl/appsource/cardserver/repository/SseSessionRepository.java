package nl.appsource.cardserver.repository;

import nl.appsource.cardserver.model.SseSession;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.data.couchbase.repository.ReactiveCouchbaseRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface SseSessionRepository extends ReactiveCouchbaseRepository<SseSession, String> {
    Flux<SseSession> findByCreator(String userId);
    Mono<Boolean> existsByCreator(String userId);
    Mono<SseSession> findByIdAndCreator(String s, String userId);

    @Query("SELECT count(*) > 0 FROM #{#n1ql.bucket} WHERE #{#n1ql.filter} AND (META().id=$appIdentifier OR creator=$userId) AND host=$hostname")
    Mono<Boolean> eventLocalRelevance(String appIdentifier, String userId, String hostname);
}
