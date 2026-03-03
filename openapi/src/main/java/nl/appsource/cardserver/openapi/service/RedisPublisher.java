package nl.appsource.cardserver.openapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public class RedisPublisher {

    private final ReactiveRedisTemplate<String, MyServerSentEvent> reactiveRedisTemplate;

    public Mono<Long> publish(final String topic, final MyServerSentEvent myServerSentEvent) {
        //log.info("Publishing message to topic {}: {}", topic, message);
        return reactiveRedisTemplate.convertAndSend(topic, myServerSentEvent)
            .doOnError(e -> log.error("Error publishing message", e));
    }

    public Mono<Void> publish(final Flux<String> topic, final MyServerSentEvent myServerSentEvent) {
        return topic.flatMap(t -> publish(t, myServerSentEvent)).then();
    }

}
