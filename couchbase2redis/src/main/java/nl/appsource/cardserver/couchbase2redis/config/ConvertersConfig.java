package nl.appsource.cardserver.couchbase2redis.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(nl.appsource.cardserver.converters.config.ConvertersConfig.class)
public class ConvertersConfig {
}
