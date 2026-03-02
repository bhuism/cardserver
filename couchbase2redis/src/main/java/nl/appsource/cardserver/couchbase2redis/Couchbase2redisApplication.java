package nl.appsource.cardserver.couchbase2redis;

import nl.appsource.cardserver.openapi.config.RedisConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(RedisConfiguration.class)
@ComponentScan(basePackages = {"nl.appsource.cardserver.converters", "nl.appsource.cardserver.couchbase2redis"})
public class Couchbase2redisApplication {

    public static void main(final String[] args) {
        SpringApplication.run(Couchbase2redisApplication.class, args);
    }

}
