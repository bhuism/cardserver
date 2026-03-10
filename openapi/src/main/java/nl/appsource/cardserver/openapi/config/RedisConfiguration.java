package nl.appsource.cardserver.openapi.config;

import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import nl.appsource.generated.openapi.model.GameEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor
public class RedisConfiguration {

    private final JsonMapper jsonMapper;

    @Bean
    public ReactiveRedisTemplate<String, MyServerSentEvent> reactiveRedisTemplateMyServerSentEvents(final ReactiveRedisConnectionFactory factory) {

        final JacksonJsonRedisSerializer<MyServerSentEvent> serializer = new JacksonJsonRedisSerializer<>(jsonMapper, MyServerSentEvent.class);
        final StringRedisSerializer stringSerializer = new StringRedisSerializer();

        RedisSerializationContext<String, MyServerSentEvent> context = RedisSerializationContext
            .<String, MyServerSentEvent>newSerializationContext(stringSerializer)
            .value(serializer)
            .hashValue(serializer)
            .hashKey(stringSerializer)
            .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

    @Bean
    public ReactiveRedisTemplate<String, GameEvent> reactiveRedisTemplateGameEvent(final ReactiveRedisConnectionFactory factory) {

        final JacksonJsonRedisSerializer<GameEvent> serializer = new JacksonJsonRedisSerializer<>(jsonMapper, GameEvent.class);
        final StringRedisSerializer stringSerializer = new StringRedisSerializer();
        final JacksonJsonRedisSerializer<Object> objectSerializer = new JacksonJsonRedisSerializer<>(jsonMapper, Object.class);

        RedisSerializationContext<String, GameEvent> context = RedisSerializationContext
            .<String, GameEvent>newSerializationContext(stringSerializer)
            .value(serializer)
            .hashValue(objectSerializer)
            .hashKey(stringSerializer)
            .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

    @Bean
    public ReactiveRedisMessageListenerContainer container(final ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveRedisMessageListenerContainer(connectionFactory);
    }

}
