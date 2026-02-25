package nl.appsource.cardserver.service.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisPublisher {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    public Mono<Long> publish(String topic, String message) {
        log.info("Publishing message to topic {}: {}", topic, message);
        return reactiveRedisTemplate.convertAndSend(topic, message)
            .doOnSuccess(count -> log.info("Published to {} clients", count))
            .doOnError(e -> log.error("Error publishing message", e));
    }

}
