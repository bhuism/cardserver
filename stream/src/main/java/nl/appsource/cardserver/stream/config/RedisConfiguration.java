package nl.appsource.cardserver.stream.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Configuration
@Import(nl.appsource.cardserver.openapi.config.RedisConfiguration.class)
@Profile("!citest")
public class RedisConfiguration {
}
