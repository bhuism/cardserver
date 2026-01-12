package nl.appsource.cardserver.repository;

import nl.appsource.cardserver.model.Boom;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.data.couchbase.repository.ReactiveCouchbaseRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface BoomRepository extends ReactiveCouchbaseRepository<Boom, String>, GenericRepository<Boom, String> {

    @Query("SELECT META(#{#n1ql.bucket}).id FROM #{#n1ql.bucket} WHERE #{#n1ql.filter} AND ( creator=$userId OR ANY p IN players SATISFIES p=$userId END ) ORDER BY updated DESC LIMIT $limit")
    Flux<String> findByUserId(String userId, Integer limit);

    @Query("#{#n1ql.selectEntity} WHERE #{#n1ql.filter} AND ( creator=$userId OR ANY p IN players SATISFIES p=$userId END ) ORDER BY updated DESC LIMIT $limit")
    Flux<Boom> findBoomsByUserId(String userId, Integer limit);

}
