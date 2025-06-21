package nl.appsource.cardserver.repository;

import nl.appsource.cardserver.model.User;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends CouchbaseRepository<User, String> {

    Optional<User> findOptionalByEmail(String email);

    @Query("#{#n1ql.selectEntity} WHERE #{#n1ql.filter} AND ANY inv IN invites SATISFIES inv = $id END ORDER BY updated DESC")
    List<User> findIncomingInvites(@Param("id") String id);

    @Query("#{#n1ql.selectEntity} WHERE #{#n1ql.filter} AND (email=$searchString OR name=$searchString OR displayNAme=$searchString)")
    Optional<User> findOptionalBySearchString(@Param("searchString") String searchString);


}
