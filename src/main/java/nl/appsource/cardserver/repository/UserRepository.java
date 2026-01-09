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

    @Query("SELECT meta(#{#n1ql.bucket}).id FROM #{#n1ql.bucket} WHERE #{#n1ql.filter} AND ANY inv IN invites SATISFIES inv = $id END ORDER BY updated DESC")
    Flux<String> findIncomingInvites(@Param("id") String id);

    @Query("#{#n1ql.selectEntity} WHERE #{#n1ql.filter} AND (LOWER(email)=LOWER($searchString) OR LOWER(name)=LOWER($searchString) OR LOWER(displayName)=LOWER($searchString)) OR META().id=$searchString")
    Flux<User> searchInvitees(@Param("searchString") String searchString);

    Mono<Boolean> existsByDisplayNameAndIdNot(String displayName, String id);

    String FRIENDS = "SELECT meta(#{#n1ql.bucket}).id"
        + " FROM #{#n1ql.bucket}"
        + " WHERE #{#n1ql.filter}"
        + " AND ARRAY_CONTAINS(invites, $userId)"
        + " AND ARRAY_CONTAINS((SELECT RAW t.invites FROM #{#n1ql.bucket} AS t USE KEYS $userId)[0], meta(#{#n1ql.bucket}).id)";

    @Query(FRIENDS)
    Flux<String> getFriends(String userId);

    String ONLINE_FRIENDS = FRIENDS + " AND ARRAY_CONTAINS((SELECT RAW s.creator FROM  #{#n1ql.bucket} AS s WHERE s._class='nl.appsource.cardserver.model.SseSession'), meta(#{#n1ql.bucket}).id)";

    @Query(ONLINE_FRIENDS)
    Flux<String> getOnlineFriends(String userId);

}
