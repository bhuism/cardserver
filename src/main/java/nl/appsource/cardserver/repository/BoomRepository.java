package nl.appsource.cardserver.repository;

import nl.appsource.cardserver.model.Boom;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.data.couchbase.repository.ReactiveCouchbaseRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface BoomRepository extends ReactiveCouchbaseRepository<Boom, String> {

    @Query("SELECT META(#{#n1ql.bucket}).id FROM #{#n1ql.bucket} WHERE #{#n1ql.filter} AND ( creator=$userId OR ANY p IN players SATISFIES p=$userId END ) ORDER BY updated DESC LIMIT $limit")
    Flux<String> findByUserId(String userId, Integer limit);

    @Query("#{#n1ql.selectEntity} WHERE #{#n1ql.filter} AND ( creator=$userId OR ANY p IN players SATISFIES p=$userId END ) ORDER BY updated DESC LIMIT $limit")
    Flux<Boom> findBoomsByUserId(String userId, Integer limit);

    @Query("SELECT EXISTS (SELECT 1 FROM #{#n1ql.bucket} b WHERE #{#n1ql.filter} AND META(b).id == $boomId AND ARRAY_LENGTH(b.games) == 16) AND NOT EXISTS(SELECT 1 FROM #{#n1ql.bucket} g WHERE g IN b.games AND ARRAY_LENGTH(g.turns) == 16)")
    Mono<Boolean> isBoomComplete(String boomId);

}
