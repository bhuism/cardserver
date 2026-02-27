package nl.appsource.cardserver.couchbase2redis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"nl.appsource.cardserver.converters", "nl.appsource.cardserver.couchbase2redis"})
public class Couchbase2redisApplication {

    public static void main(final String[] args) {
        SpringApplication.run(Couchbase2redisApplication.class, args);
    }

}
