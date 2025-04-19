package nl.appsource.cardserver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@Slf4j
@EnableWebSecurity
//@EnableTransactionManagement(proxyTargetClass = true)
@EnableJpaRepositories
@SpringBootApplication
public class CardServerApplication {

    public CardServerApplication() {
        log.info("Hi!");
        Runtime.getRuntime().addShutdownHook(new Thread() {
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
