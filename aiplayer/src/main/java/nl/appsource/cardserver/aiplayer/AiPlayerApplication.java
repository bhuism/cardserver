package nl.appsource.cardserver.aiplayer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"nl.appsource.cardserver.converters", "nl.appsource.cardserver.couchbase2redis", "nl.appsource.cardserver.couchbase"})
public class AiPlayerApplication {

    public static void main(final String[] args) {
        SpringApplication.run(AiPlayerApplication.class, args);
    }

}
