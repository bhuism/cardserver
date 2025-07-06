package nl.appsource.cardserver.repository;

import nl.appsource.cardserver.model.User;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.data.couchbase.repository.ReactiveCouchbaseRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface UserRepository extends ReactiveCouchbaseRepository<User, String> {

    Mono<User> findByEmail(String email);

    @Query("#{#n1ql.selectEntity} WHERE #{#n1ql.filter} AND ANY inv IN invites SATISFIES inv = $id END ORDER BY updated DESC")
    Flux<User> findIncomingInvites(@Param("id") String id);

    @Query("#{#n1ql.selectEntity} WHERE #{#n1ql.filter} AND (email=$searchString OR LOWER(name)=LOWER($searchString) OR LOWER(displayName)=LOWER($searchString)) OR META().id=$searchString")
    Flux<User> searchInvitees(@Param("searchString") String searchString);

    Mono<Boolean> existsByDisplayName(String displayName);
}
