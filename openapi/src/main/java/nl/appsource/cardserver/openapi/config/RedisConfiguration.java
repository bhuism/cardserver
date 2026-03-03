package nl.appsource.cardserver.openapi.config;

import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor
public class RedisConfiguration {

    private final JsonMapper jsonMapper;

    @Bean
    public ReactiveRedisTemplate<String, MyServerSentEvent> reactiveRedisTemplate(final ReactiveRedisConnectionFactory factory) {

        final JacksonJsonRedisSerializer<MyServerSentEvent> serializer = new JacksonJsonRedisSerializer<>(jsonMapper, MyServerSentEvent.class);

        RedisSerializationContext<String, MyServerSentEvent> context = RedisSerializationContext
            .<String, MyServerSentEvent>newSerializationContext(serializer)
            .value(serializer)
            .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

    @Bean
    public ReactiveRedisMessageListenerContainer container(final ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveRedisMessageListenerContainer(connectionFactory);
    }

}
