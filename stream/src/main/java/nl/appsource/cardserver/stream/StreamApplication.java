package nl.appsource.cardserver.stream;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.couchbase.config.CouchbaseConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@Slf4j
@SpringBootApplication(scanBasePackageClasses = CouchbaseConfiguration.class)
@ComponentScan(basePackages = {"nl.appsource.cardserver.stream", "nl.appsource.cardserver.couchbase", "nl.appsource.cardserver.converters"})
public class StreamApplication {

    public StreamApplication() {
        log.info("Hi!");

        ECKey jwk = null;
        try {
            jwk = new ECKeyGenerator(Curve.P_521)
                .keyID("es512-key-1") // Optional: assigning a Key ID (kid) is recommended for JWK sets
                .generate();
            // The generated JWK contains both public and private parameters.
            System.out.println("Full JWK (Keep Private): " + jwk.toJSONString());
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }


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
