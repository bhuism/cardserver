package nl.appsource.cardserver.repository;

import nl.appsource.cardserver.model.Feedback;
import org.springframework.data.couchbase.repository.ReactiveCouchbaseRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedbackRepository extends ReactiveCouchbaseRepository<Feedback, String> {

}
