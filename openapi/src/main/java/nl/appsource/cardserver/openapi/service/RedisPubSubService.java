package nl.appsource.cardserver.openapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class RedisPubSubService {

    private final ReactiveRedisTemplate<String, MyServerSentEvent> reactiveRedisTemplate;

    private final ReactiveRedisMessageListenerContainer container;

    private final JsonMapper jsonMapper;

    public Mono<Long> broadCast(final String topic, final MyServerSentEvent myServerSentEvent) {
        log.info("Publishing message to topic {}: {}", topic, myServerSentEvent);
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


}
