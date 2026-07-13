package nl.appsource.cardserver.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class StreamApplication {

    public static void main(final String[] args) {
        SpringApplication.run(StreamApplication.class, args);
    }

}
