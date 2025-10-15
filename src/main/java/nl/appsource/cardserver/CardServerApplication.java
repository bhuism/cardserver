package nl.appsource.cardserver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;

@Slf4j
@SpringBootApplication
    (exclude = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class, SessionAutoConfiguration.class})
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
