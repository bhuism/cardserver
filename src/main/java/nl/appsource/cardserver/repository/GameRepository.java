package nl.appsource.cardserver.repository;

import nl.appsource.cardserver.model.Game;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.data.querydsl.ListQuerydslPredicateExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GameRepository extends CouchbaseRepository<Game, String>, ListQuerydslPredicateExecutor<Game> {

//    @Query("SELECT meta(#{#n1ql.bucket}).id FROM #{#n1ql.bucket} WHERE #{#n1ql.filter} AND creator = $creator")
//    Set<String> findIdByCreator(@Param("creator") String creator);

//    @Query("SELECT META(g).id FROM #{#n1ql.bucket} u JOIN #{#n1ql.bucket} g ON g.creator = META(u).id WHERE g._class = 'nl.appsource.cardserver.model.Game' AND u._class = 'nl.appsource.cardserver.model.User' AND u.email= $email ORDER BY g.updated DESC")
//    Set<String> findIdByEmail(@Param("email") String email);
//
//    @Query("SELECT g.*,META(g).id AS __id FROM #{#n1ql.bucket} u JOIN #{#n1ql.bucket} g ON g.creator = META(u).id WHERE g._class = 'nl.appsource.cardserver.model.Game' AND u._class = 'nl.appsource.cardserver.model.User' AND u.email= $email ORDER BY g.updated DESC")
//    List<Game> findByEmail(@Param("email") String email);
    //@Query("#{#n1ql.selectEntity} WHERE #{#n1ql.filter} AND ANY inv IN invites SATISFIES inv = $id END ORDER BY updated DESC")

    @Query(value = "#{#n1ql.selectEntity} WHERE #{#n1ql.filter} AND ( creator=$userId OR ANY p IN players SATISFIES p=$userId END ) ORDER BY updated DESC", readonly = true)
    List<Game> findByUserId(@Param("userId") String userId);

    @Query(value = "#{#n1ql.selectEntity} WHERE #{#n1ql.filter} AND META().id=$gameId AND ( creator=$userId OR ANY p IN players SATISFIES p=$userId END ) ORDER BY updated DESC", readonly = true)
    Optional<Game> findByUserIdAndGameId(@Param("userId") String userId, @Param("gameId") String gameId);


}
