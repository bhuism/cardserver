package nl.appsource.cardserver;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.openapi.config.RedisConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Slf4j
@SpringBootApplication
@Import(RedisConfiguration.class)
public class CardServerApplication {

    public CardServerApplication() {
        log.info("Hi!");
        Runtime.getRuntime()
            .addShutdownHook(new Thread() {
                @Override
                public void run() {
                    super.run();
                    log.info("Bye!");
                }
            });
    }

    public static void main(final String[] args) {
        SpringApplication.run(CardServerApplication.class, args);
    }


}
