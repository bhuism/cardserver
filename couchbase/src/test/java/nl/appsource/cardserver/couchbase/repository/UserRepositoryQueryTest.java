package nl.appsource.cardserver.couchbase.repository;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.SseSession;
import nl.appsource.cardserver.model.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

@Slf4j
@SpringBootTest
public class UserRepositoryQueryTest {

    static CouchbaseContainer couchbase = new CouchbaseContainer("couchbase/server:7.6.0")
        .withStartupAttempts(3)
        .withCredentials("Administrator", "password")
        .withBucket(new BucketDefinition("cardserver"))
        .withStartupTimeout(java.time.Duration.ofMinutes(3))
        .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withUlimits(List.of(
            new com.github.dockerjava.api.model.Ulimit("nofile", 200000L, 200000L)
        )));

    static com.couchbase.client.java.Cluster cluster;

    @DynamicPropertySource
    static void couchbaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.couchbase.connection-string", couchbase::getConnectionString);
        registry.add("spring.couchbase.username", couchbase::getUsername);
        registry.add("spring.couchbase.password", couchbase::getPassword);
        registry.add("spring.data.couchbase.bucket-name", () -> "cardserver");
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SseSessionRepository sseSessionRepository;

    @BeforeAll
    static void beforeAll() {
        couchbase.start();

//        try {
//            cluster.query("CREATE PRIMARY INDEX IF NOT EXISTS ON `cardserver`");
//        } catch (Exception e) {
//            // ignore
//        } finally {
//            cluster.disconnect();
//        }
    }

    @AfterAll
    static void afterAll() {
        couchbase.stop();
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        log.info("Cleaning database");
        cluster = com.couchbase.client.java.Cluster.connect(
            couchbase.getConnectionString(),
            couchbase.getUsername(),
            couchbase.getPassword()
        );

        cluster.query("DELETE FROM `cardserver`");

//        cluster.buckets().flushBucket("cardserver");
//        cluster.query("CREATE PRIMARY INDEX IF NOT EXISTS ON `cardserver`");
//
//        final Set<String> indexes = cluster.queryIndexes().getAllIndexes("cardserver").stream().map(QueryIndex::name).collect(Collectors.toSet());
//
//        indexes.forEach(queryIndex -> {
//            log.info("found index name: " + queryIndex);
//        });
//
//        cluster.queryIndexes().watchIndexes("cardserver", indexes, Duration.ofSeconds(60));


        log.info("Database cleaned");
    }

    @AfterEach
    void tearDown() {
//        cluster.buckets().flushBucket("cardserver");
        cluster.query("DELETE FROM `cardserver`");
        cluster.disconnect();
    }

    private User createUser(String id, String email, String name, String displayName, List<String> invites) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setName(name);
        user.setDisplayName(displayName);
        user.setProviderId("google");
        user.setInvites(invites != null ? invites : List.of());
        user.setUpdated(Instant.now());
        return user;
    }

    @Test
    void testFindByEmail() {
        User user = createUser("user1", "test@example.com", "Test User", "Test", List.of());
        userRepository.save(user).block();

        userRepository.findByEmail("test@example.com")
            .as(StepVerifier::create)
            .expectNextMatches(found -> found.getId().equals("user1"))
            .verifyComplete();
    }

    @Test
    void testFindIncomingInvites() {
        User user1 = createUser("user1", "user1@example.com", "User One", "One", List.of("targetUser"));
        User user2 = createUser("user2", "user2@example.com", "User Two", "Two", List.of("otherUser"));

        userRepository.saveAll(List.of(user1, user2)).blockLast();

        userRepository.findIncomingInvites("targetUser")
            .as(StepVerifier::create)
            .expectNext("user1")
            .verifyComplete();
    }

    @Test
    void testSearchInvitees() {
        User user = createUser("user123", "alice@example.com", "Alice Smith", "Alice", List.of());
        userRepository.save(user).block();

        userRepository.searchInvitees("otherUser", "Alice")
            .as(StepVerifier::create)
            .expectNextMatches(found -> found.getId().equals("user123"))
            .verifyComplete();
    }

    @Test
    void testExistsByDisplayNameAndIdNot() {
        User user = createUser("user1", "user1@example.com", "User One", "UniqueName", List.of());
        userRepository.save(user).block();

        userRepository.existsByDisplayNameAndIdNot("UniqueName", "user2")
            .as(StepVerifier::create)
            .expectNext(true)
            .verifyComplete();

        userRepository.existsByDisplayNameAndIdNot("UniqueName", "user1")
            .as(StepVerifier::create)
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    void testGetFriendsAndFriendIds() {
        User user1 = createUser("user1", "user1@example.com", "User One", "One", List.of("user2"));
        User user2 = createUser("user2", "user2@example.com", "User Two", "Two", List.of("user1"));

        userRepository.saveAll(List.of(user1, user2)).blockLast();

        userRepository.getFriends("user1")
            .as(StepVerifier::create)
            .expectNextMatches(f -> f.getId().equals("user2"))
            .verifyComplete();

        userRepository.getFriendIds("user1")
            .as(StepVerifier::create)
            .expectNext("user2")
            .verifyComplete();
    }

    @Test
    void testGetOnlineFriends() {
        User user1 = createUser("user1", "user1@example.com", "User One", "One", List.of("user2"));
        User user2 = createUser("user2", "user2@example.com", "User Two", "Two", List.of("user1"));

        userRepository.saveAll(List.of(user1, user2)).blockLast();

        SseSession session = new SseSession("session1", "127.0.0.1", "Mozilla", "localhost", "user2");
        sseSessionRepository.save(session).block();

        userRepository.getOnlineFriends("user1")
            .as(StepVerifier::create)
            .expectNext("user2")
            .verifyComplete();
    }
}
