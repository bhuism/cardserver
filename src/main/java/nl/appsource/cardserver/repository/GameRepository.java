package nl.appsource.cardserver.repository;

import nl.appsource.cardserver.model.Game;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.data.querydsl.ListQuerydslPredicateExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface GameRepository extends CouchbaseRepository<Game, String>, ListQuerydslPredicateExecutor<Game> {

//    @Query("SELECT meta(#{#n1ql.bucket}).id FROM #{#n1ql.bucket} WHERE #{#n1ql.filter} AND creator = $creator")
//    Set<String> findIdByCreator(@Param("creator") String creator);

    @Query("SELECT META(g).id FROM #{#n1ql.bucket} u JOIN #{#n1ql.bucket} g ON g.creator = META(u).id WHERE g._class = 'nl.appsource.cardserver.model.Game' AND u._class = 'nl.appsource.cardserver.model.User' AND u.email= $email ORDER BY g.updated DESC")
    Set<String> findIdByEmail(@Param("email") String email);

    @Query("SELECT g.*,META(g).id AS __id FROM #{#n1ql.bucket} u JOIN #{#n1ql.bucket} g ON g.creator = META(u).id WHERE g._class = 'nl.appsource.cardserver.model.Game' AND u._class = 'nl.appsource.cardserver.model.User' AND u.email= $email ORDER BY g.updated DESC")
    List<Game> findByEmail(@Param("email") String email);

}
