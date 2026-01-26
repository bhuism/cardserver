package nl.appsource.cardserver.repository;

import nl.appsource.cardserver.model.Boom;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.data.couchbase.repository.ReactiveCouchbaseRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface BoomRepository extends ReactiveCouchbaseRepository<Boom, String> {

    @Query(value = "SELECT META(#{#n1ql.bucket}).id FROM #{#n1ql.bucket} WHERE #{#n1ql.filter} AND ( creator=$userId OR ANY p IN players SATISFIES p=$userId END ) ORDER BY updated DESC LIMIT $limit", readonly = true)
    Flux<String> findByUserId(String userId, Integer limit);

    @Query(value = "#{#n1ql.selectEntity} WHERE #{#n1ql.filter} AND ( creator=$userId OR ANY p IN players SATISFIES p=$userId END ) ORDER BY updated DESC LIMIT $limit", readonly = true)
    Flux<Boom> findBoomsByUserId(String userId, Integer limit);

    @Query(value = "SELECT EXISTS(SELECT RAW true " +
        "FROM #{#n1ql.bucket} AS b USE KEYS $1 " +
        "WHERE b._class = 'nl.appsource.cardserver.model.Boom' " +
        "AND ARRAY_LENGTH(b.games) = 16 " +
        "AND NOT EXISTS (" +
        "    SELECT 1 " +
        "    FROM #{#n1ql.bucket} AS g USE KEYS b.games " +
        "    WHERE ARRAY_LENGTH(g.turns) = 16" +
        "))", readonly = true)
    Mono<Boolean> isBoomComplete(String boomId);

}
