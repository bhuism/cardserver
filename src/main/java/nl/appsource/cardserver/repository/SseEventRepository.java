package nl.appsource.cardserver.repository;

import nl.appsource.cardserver.model.SseEvent;
import org.springframework.data.couchbase.repository.ReactiveCouchbaseRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SseEventRepository extends ReactiveCouchbaseRepository<SseEvent, String> {
}
