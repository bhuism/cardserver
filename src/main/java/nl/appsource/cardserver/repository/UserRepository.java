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

    @Query(value = "#{#n1ql.selectEntity} WHERE #{#n1ql.filter} AND ANY inv IN invites SATISFIES inv = $id END ORDER BY lastLogin DESC", readonly = true)
    List<User> findIncomingInvites(@Param("id") String id);

    @Query(value = "#{#n1ql.selectEntity} WHERE #{#n1ql.filter} AND (LOWER(email)=LOWER($searchString) OR LOWER(name)=LOWER($searchString) OR LOWER(displayName)=LOWER($searchString)) ORDER BY lastLogin DESC", readonly = true)
    List<User> findInvitees(@Param("searchString") String searchString);

    Optional<User> findByDisplayName(String displayName);
}
