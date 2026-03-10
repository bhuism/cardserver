package nl.appsource.cardserver.openapi.config;

import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.openapi.MyServerSentEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@Profile("!citest")
@RequiredArgsConstructor
public class RedisConfiguration {

    private final JsonMapper jsonMapper;

    @Bean
    public ReactiveRedisTemplate<String, MyServerSentEvent> reactiveRedisTemplateMyServerSentEvents(final ReactiveRedisConnectionFactory factory) {

        final JacksonJsonRedisSerializer<MyServerSentEvent> serializer = new JacksonJsonRedisSerializer<>(jsonMapper, MyServerSentEvent.class);

        RedisSerializationContext<String, MyServerSentEvent> context = RedisSerializationContext
            .<String, MyServerSentEvent>newSerializationContext(serializer)
            .value(serializer)
            .hashValue(serializer)
            .hashKey(serializer)
            .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

    @Bean
    public ReactiveRedisMessageListenerContainer container(final ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveRedisMessageListenerContainer(connectionFactory);
    }

//    @Bean
//    public Converter<MyServerSentEvent, byte[]> sse2byteArray() {
//        return jsonMapper::writeValueAsBytes;
//    }
//
//    @Bean
//    public Converter<byte[], MyServerSentEvent> byte2sse() {
//        return source -> jsonMapper.readValue(source, MyServerSentEvent.class);
//    }
//
//    @Bean
//    public RedisCustomConversions redisCustomConversions(final Converter<MyServerSentEvent, byte[]> sse2byteArray,
//                                                         final Converter<byte[], MyServerSentEvent> byte2sse) {
//        return new RedisCustomConversions(List.of(sse2byteArray, byte2sse));
//    }

}
