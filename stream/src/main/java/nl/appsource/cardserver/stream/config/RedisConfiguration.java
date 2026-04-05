package nl.appsource.cardserver.stream.config;

import nl.appsource.cardserver.openapi.MyServerSentEvent;
import nl.appsource.cardserver.openapi.service.RedisPubSubService;
import nl.appsource.cardserver.openapi.service.SseEventSender;
import nl.appsource.cardserver.openapi.service.SseEventSenderImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@Import(nl.appsource.cardserver.openapi.config.RedisConfiguration.class)
@Profile("!citest")
public class RedisConfiguration {

    @Bean
    public RedisPubSubService redisPubSubService(final ReactiveRedisTemplate<String, MyServerSentEvent> reactiveRedisTemplate,
                                                 final ReactiveRedisMessageListenerContainer container,
                                                 final JsonMapper jsonMapper) {
        return new RedisPubSubService(reactiveRedisTemplate, container, jsonMapper);
    }

    @Bean
    public SseEventSender sseEventSender(final RedisPubSubService redisPubSubService) {
        return new SseEventSenderImpl(redisPubSubService);
    }

}
