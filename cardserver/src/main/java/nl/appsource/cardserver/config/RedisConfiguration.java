package nl.appsource.cardserver.config;

import nl.appsource.cardserver.openapi.service.RedisPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

@Configuration
@Import(nl.appsource.cardserver.openapi.config.RedisConfiguration.class)
public class RedisConfiguration {

    @Bean
    public RedisPublisher redisPublisher(final ReactiveRedisTemplate<String, String> reactiveRedisTemplate) {
        return new RedisPublisher(reactiveRedisTemplate);
    }

}
