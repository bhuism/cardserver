package nl.appsource.cardserver.openapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
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

    public Disposable consumeAndProcess(final String queueName, final Function<MyServerSentEvent, Mono<Void>> messageProcessor) {

        final String processingQueue = "task-queue-processing-" + queueName + UUID.randomUUID();

        return reactiveRedisTemplate.opsForList()
            .rightPopAndLeftPush(queueName, processingQueue, Duration.ofSeconds(5))
            .flatMap(message ->
                // Execute the injected business logic
                messageProcessor.apply(message)
                    // SUCCESS PATH: Acknowledge by removing from the processing queue
                    .then(reactiveRedisTemplate.opsForList().remove(processingQueue, 1, message))

                    // ERROR PATH: Handle failures localized to this specific message
                    .onErrorResume(error -> {
                        System.err.println("Business logic failed for: " + message + " | Error: " + error.getMessage());
                        // Acknowledge (remove) the failed message to prevent infinite retries
                        return reactiveRedisTemplate.opsForList().remove(processingQueue, 1, message);
                    })
            )
            // GLOBAL ERROR PATH: Handle Redis connection drops (prevents termination of the repeat loop)
            .onErrorResume(e -> {
                System.err.println("Redis connection error: " + e.getMessage());
                return Mono.delay(Duration.ofSeconds(2));
            })
            // Loop indefinitely
            .repeat()
            // Trigger execution and run in the background
            .subscribe();

    }

    public Mono<Long> publishList(final String queueName, final MyServerSentEvent message) {
        return reactiveRedisTemplate.opsForList()
            .leftPush(queueName, message)
//            .doOnSuccess(listSize -> System.out.println("Published: " + message + " | Queue size: " + listSize))
            .doOnError(e -> System.err.println("Failed to publish message: " + e.getMessage()));
    }


}
