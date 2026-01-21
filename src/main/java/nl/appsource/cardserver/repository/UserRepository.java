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

    @Query(value = "SELECT meta(#{#n1ql.bucket}).id FROM #{#n1ql.bucket} WHERE #{#n1ql.filter} AND ANY inv IN invites SATISFIES inv = $id END ORDER BY updated DESC", readonly = true)
    Flux<String> findIncomingInvites(@Param("id") String id);

    @Query(value = "#{#n1ql.selectEntity} WHERE #{#n1ql.filter} AND (LOWER(email)=LOWER($searchString) OR LOWER(name)=LOWER($searchString) OR LOWER(displayName)=LOWER($searchString)) OR META().id=$searchString", readonly = true)
    Flux<User> searchInvitees(@Param("searchString") String searchString);

    Mono<Boolean> existsByDisplayNameAndIdNot(String displayName, String id);

    String FRIENDS = "#{#n1ql.selectEntity} WHERE #{#n1ql.filter}"
        + " AND ARRAY_CONTAINS(invites, $userId)"
        + " AND ARRAY_CONTAINS((SELECT RAW t.invites FROM #{#n1ql.bucket} AS t USE KEYS $userId)[0], meta(#{#n1ql.bucket}).id)";

    @Query(value = FRIENDS, readonly = true)
    Flux<User> getFriends(String userId);

    String FRIENDIDS = "SELECT meta(#{#n1ql.bucket}).id"
        + " FROM #{#n1ql.bucket}"
        + " WHERE #{#n1ql.filter}"
        + " AND ARRAY_CONTAINS(invites, $userId)"
        + " AND ARRAY_CONTAINS((SELECT RAW t.invites FROM #{#n1ql.bucket} AS t USE KEYS $userId)[0], meta(#{#n1ql.bucket}).id)";

    String ONLINE_FRIENDIDS = FRIENDIDS + " AND ARRAY_CONTAINS((SELECT RAW s.creator FROM  #{#n1ql.bucket} AS s WHERE s._class='nl.appsource.cardserver.model.SseSession'), meta(#{#n1ql.bucket}).id)";

    @Query(value = ONLINE_FRIENDIDS, readonly = true)
    Flux<String> getOnlineFriends(String userId);

}
