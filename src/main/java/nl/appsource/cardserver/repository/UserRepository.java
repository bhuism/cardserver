package nl.appsource.cardserver.repository;

import nl.appsource.cardserver.model.User;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface UserRepository extends CouchbaseRepository<User, String> {

    Optional<User> findOptionalByEmail(String email);

    @Query("#{#n1ql.selectEntity} WHERE #{#n1ql.filter} AND ANY invites SATISFIES invites = $id")
    Set<User> findAllIncomingInvites(@Param("id") String id);

}
