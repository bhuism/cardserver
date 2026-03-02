package nl.appsource.cardserver.stream;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.couchbase.config.CouchbaseConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication(scanBasePackageClasses = CouchbaseConfiguration.class)
public class StreamApplication {

    public StreamApplication() {
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
        SpringApplication.run(StreamApplication.class, args);
    }


}
