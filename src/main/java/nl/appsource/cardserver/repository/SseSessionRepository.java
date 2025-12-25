package nl.appsource.cardserver.repository;

import nl.appsource.cardserver.model.SseSession;
import org.springframework.data.couchbase.repository.ReactiveCouchbaseRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface SseSessionRepository extends ReactiveCouchbaseRepository<SseSession, String> {
    Flux<SseSession> findByUserId(String userId);
}
