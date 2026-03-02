package nl.appsource.cardserver.stream.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(nl.appsource.cardserver.openapi.config.RedisConfiguration.class)
public class RedisConfiguration {
}
