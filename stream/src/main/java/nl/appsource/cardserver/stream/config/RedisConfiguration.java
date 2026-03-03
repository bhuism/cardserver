package nl.appsource.cardserver.stream.config;

import nl.appsource.cardserver.openapi.service.PubSubService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;

@Configuration
@Import(nl.appsource.cardserver.openapi.config.RedisConfiguration.class)
@Profile("!citest")
public class RedisConfiguration {

    @Bean
    PubSubService pubSubService(final ReactiveRedisMessageListenerContainer reactiveRedisMessageListenerContainer) {
        return new PubSubService(reactiveRedisMessageListenerContainer);
    }
    
}
