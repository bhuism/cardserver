package nl.appsource.cardserver.repository;

import nl.appsource.cardserver.model.Game;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GameRepository extends CouchbaseRepository<Game, String> {

}
