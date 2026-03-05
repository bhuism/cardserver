package nl.appsource.cardserver.openapi.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.ReactiveSubscription.Message;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class RedisSubscriber {

    private final ReactiveRedisMessageListenerContainer container;

    public Flux<Message<String, String>> listenTo(final String topicName) {
        return container.receive(ChannelTopic.of(topicName));
    }

}
