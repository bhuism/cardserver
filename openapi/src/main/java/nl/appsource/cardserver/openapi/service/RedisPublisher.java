package nl.appsource.cardserver.openapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public class RedisPublisher {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    public Mono<Long> publish(final String topic, final String message) {
        //log.info("Publishing message to topic {}: {}", topic, message);
        return reactiveRedisTemplate.convertAndSend(topic, message)
            .doOnError(e -> log.error("Error publishing message", e));
    }

    public Mono<Void> publish(final Flux<String> topic, final String message) {
        return topic.flatMap(t -> publish(t, message)).then();
    }

}
