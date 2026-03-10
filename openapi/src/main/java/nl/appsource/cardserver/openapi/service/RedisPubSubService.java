package nl.appsource.cardserver.openapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStreamOperations;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor
public class RedisPubSubService {

    private final ReactiveRedisTemplate<String, MyServerSentEvent> reactiveRedisTemplate;

    private final ReactiveRedisMessageListenerContainer container;

    private final JsonMapper jsonMapper;

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

    public Mono<Long> broadCast(final String topic, final MyServerSentEvent myServerSentEvent) {
        //log.info("Publishing message to topic {}: {}", topic, message);
        return reactiveRedisTemplate.convertAndSend(topic, myServerSentEvent)
            .doOnError(e -> log.error("Error publishing message", e));
    }

    public Mono<Void> broadCast(final Flux<String> topic, final MyServerSentEvent myServerSentEvent) {
        return topic.distinct().flatMap(t -> broadCast(t, myServerSentEvent)).then();
    }

    public Flux<MyServerSentEvent> listenTo(final String... topicName) {
        return listenTo(new HashSet<>(Set.of(topicName)));
    }

    public Flux<MyServerSentEvent> listenTo(final Set<String> topicName) {
        return container.receive(topicName.stream().map(ChannelTopic::of).toArray(ChannelTopic[]::new))
            .flatMap(message -> {
                try {
                    return Mono.just(jsonMapper.readValue(message.getMessage(), MyServerSentEvent.class));
                } catch (final JacksonException e) {
                    log.error("Could not deserialize message on topic: {}", topicName, e);
                    return Mono.empty();
                }
            });
    }

    public Flux<Map.Entry<String, MyServerSentEvent>> listenToMessage(final Set<String> topicName) {
        return container.receive(topicName.stream().map(ChannelTopic::of).toArray(ChannelTopic[]::new))
            .flatMap(message -> {
                try {
                    return Mono.just(Map.entry(message.getChannel(), jsonMapper.readValue(message.getMessage(), MyServerSentEvent.class)));
                } catch (final JacksonException e) {
                    log.error("Could not deserialize message on topic: {}", topicName, e);
                    return Mono.empty();
                }
            });
    }

    //    public Disposable consumeAndProcess(final String queueName, final Function<MyServerSentEvent, Mono<Void>> messageProcessor) {
    public Disposable consumeFromStream(final String streamKey, final String groupName, final Function<MyServerSentEvent, Mono<Void>> messageProcessor) {

        final ReactiveStreamOperations<String, String, MyServerSentEvent> streamOps = reactiveRedisTemplate.opsForStream();

        final String consumerName = "consumer-" + HOSTNAME + "-" + UUID.randomUUID();

        // 1. Initialize the Consumer Group
        return streamOps.createGroup(streamKey, ReadOffset.latest(), groupName)
            .onErrorResume(e -> {
                log.error("Failed to create consumer group for stream: {} with group name: {}", streamKey, groupName, e);
                // Ignore BUSYGROUP error if the group already exists
                if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                    return Mono.empty();
                }
                return Mono.error(e);
            })

            // 2. Chain the infinite read loop to execute after group creation
            .thenMany(
                streamOps.read(
                        org.springframework.data.redis.connection.stream.Consumer.from(groupName, consumerName),
                        StreamReadOptions.empty().block(Duration.ofSeconds(5)),
                        StreamOffset.create(streamKey, ReadOffset.lastConsumed())
                    )
                    .flatMap(record ->
                        // Execute the injected business logic
                        messageProcessor.apply(record.getValue().get("payload"))
                            // SUCCESS PATH: Acknowledge the message to remove it from the PEL
                            .then(streamOps.acknowledge(groupName, record))

                            // ERROR PATH: Catch failures. Do NOT acknowledge.
                            // The message remains in the PEL for this consumer to be claimed/retried.
                            .onErrorResume(error -> {
                                log.error("Processing failed for message ID: " + record.getId() + " | Error: " + error.getMessage(), error);
                                return Mono.empty();
                            }), 1
                    )
                    // GLOBAL ERROR PATH: Handle transient Redis connection drops
                    .onErrorResume(e -> {
                        log.error("Redis connection error: " + e.getMessage(), e);
                        return Mono.delay(Duration.ofSeconds(2)).then(Mono.empty());
                    })
                    // Loop indefinitely
                    .<MyServerSentEvent>repeat()
            )
            // 3. Trigger execution and return the Disposable
            .subscribe();
    }


    public Mono<RecordId> publishToStream(final String queueName, final MyServerSentEvent message) {
        final Map<String, MyServerSentEvent> messageBody = Collections.singletonMap("payload", message);
        final MapRecord<String, String, MyServerSentEvent> record = MapRecord.create(queueName, messageBody);
        return reactiveRedisTemplate.opsForStream().add(record)
            .doOnSuccess(recordId -> log.info("Published to stream with ID: " + recordId))
            .doOnError(e -> System.err.println("Failed to publish: " + e.getMessage()));
    }


}
