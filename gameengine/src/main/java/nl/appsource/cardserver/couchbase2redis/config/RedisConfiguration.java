package nl.appsource.cardserver.couchbase2redis.config;

import nl.appsource.cardserver.openapi.MyServerSentEvent;
import nl.appsource.cardserver.openapi.service.RedisPublisher;
import nl.appsource.cardserver.openapi.service.RedisSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;

@Configuration
@Import(nl.appsource.cardserver.openapi.config.RedisConfiguration.class)
@Profile("!citest")
public class RedisConfiguration {

    @Bean
    public RedisPublisher redisPublisher(final ReactiveRedisTemplate<String, MyServerSentEvent> reactiveRedisTemplate) {
        return new RedisPublisher(reactiveRedisTemplate);
    }

    @Bean
    public RedisSubscriber redisSubscriber(final ReactiveRedisMessageListenerContainer reactiveRedisMessageListenerContainer) {
        return new RedisSubscriber(reactiveRedisMessageListenerContainer);
    }

}
