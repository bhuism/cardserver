package nl.appsource.cardserver.couchbase2redis;

import nl.appsource.cardserver.converters.config.ConvertersConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(ConvertersConfig.class)
public class Couchbase2redisApplication {

    public static void main(final String[] args) {
        SpringApplication.run(Couchbase2redisApplication.class, args);
    }

}
