package nl.appsource.cardserver.openapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.generated.openapi.model.GameEvent;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStreamOperations;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.function.Function;

import static org.springframework.data.redis.connection.stream.Consumer.from;

@Slf4j
@RequiredArgsConstructor
public class RedisStreamService {

    private final ReactiveRedisTemplate<String, GameEvent> reactiveRedisTemplate;

    private static final String HOSTNAME;

    static {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (final UnknownHostException e) {
            host = "unknown";
        }
        HOSTNAME = host;
    }

    public Disposable consumeFromStream(final String streamKey, final String groupName, final Function<ObjectRecord<String, GameEvent>, Mono<Void>> messageProcessor) {

        final ReactiveStreamOperations<String, String, GameEvent> streamOps = reactiveRedisTemplate.opsForStream();

        final String consumerName = "consumer-" + HOSTNAME;

        // 1. Initialize the Consumer Group
        return streamOps.createGroup(streamKey, ReadOffset.latest(), groupName)
            .onErrorResume(e -> {
                // Ignore BUSYGROUP error if the group already exists
                if (e.getCause() != null && e.getCause().getMessage().contains("BUSYGROUP")) {
                    return Mono.empty();
                } else {
                    log.error("Failed to create consumer group for stream: {} with group name: {} message: {}", streamKey, groupName, e.getMessage(), e);
                    return Mono.error(e);
                }
            })
            // 2. Chain the infinite read loop to execute after group creation
            .thenMany(
                streamOps.read(
                        GameEvent.class,
                        from(groupName, consumerName),
                        StreamReadOptions.empty().block(Duration.ofSeconds(5)),
                        StreamOffset.create(streamKey, ReadOffset.lastConsumed())
                    )
                    .flatMap(record ->
                        // Execute the injected business logic
                        messageProcessor.apply(record)

                            // SUCCESS PATH: Acknowledge the message to remove it from the PEL
                            .then(streamOps.acknowledge(groupName, record))

                            // ERROR PATH: Catch failures. Do NOT acknowledge.
                            // The message remains in the PEL for this consumer to be claimed/retried.
                            .onErrorResume(error -> {
                                log.error("Processing failed for message ID: {} | Error: {}", record.getId(), error.getMessage(), error);
                                return Mono.empty();
                            }), 1
                    )
                    // GLOBAL ERROR PATH: Handle transient Redis connection drops
                    .onErrorResume(e -> {
                        log.error("Redis connection error: {}", e.getMessage(), e);
                        return Mono.delay(Duration.ofSeconds(2)).then(Mono.empty());
                    })
                    // Loop indefinitely
                    .repeat()
            )
            // 3. Trigger execution and return the Disposable
            .subscribe();
    }


    public Mono<RecordId> publishToStream(final String queueName, final GameEvent gameEvent) {
        final ObjectRecord<String, GameEvent> mapRecord = ObjectRecord.create(queueName, gameEvent);
        return reactiveRedisTemplate.opsForStream().add(mapRecord)
            .doOnSuccess(recordId -> log.info("Published to stream with ID: {}", recordId))
            .doOnError(e -> System.err.println("Failed to publish: " + e.getMessage()));
    }


}
