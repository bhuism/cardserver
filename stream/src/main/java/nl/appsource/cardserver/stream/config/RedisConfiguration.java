package nl.appsource.cardserver.stream.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfiguration {

    @Bean
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(ReactiveRedisConnectionFactory factory) {
        StringRedisSerializer serializer = new StringRedisSerializer();
        RedisSerializationContext<String, String> context = RedisSerializationContext
            .<String, String>newSerializationContext(serializer)
            .value(serializer)
            .build();
        return new ReactiveRedisTemplate<>(factory, context);
    }

    @Bean
    public ReactiveRedisMessageListenerContainer container(ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveRedisMessageListenerContainer(connectionFactory);
    }

}
