package nl.appsource.cardserver.service.messaging;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisClient {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @PostConstruct
    public void init() {
        subscribe("cardserver-topic");
    }

    public void subscribe(String topic) {
        reactiveRedisTemplate.listenTo(ChannelTopic.of(topic))
            .map(ReactiveSubscription.Message::getMessage)
            .subscribe(message -> {
                log.info("Received message from topic {}: {}", topic, message);
                // Handle the message here
            }, error -> {
                log.error("Error while listening to topic {}", topic, error);
            });
    }

}
