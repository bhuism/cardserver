package nl.appsource.cardserver.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class ApiApplication {

    public static void main(final String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }

}
