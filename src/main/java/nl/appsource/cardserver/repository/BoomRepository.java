package nl.appsource.cardserver.repository;

import nl.appsource.cardserver.model.Boom;
import org.springframework.data.couchbase.repository.ReactiveCouchbaseRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BoomRepository extends ReactiveCouchbaseRepository<Boom, String> {


}
