package nl.appsource.cardserver.openapi.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "knative")
public class KnativeProperties {
    /**
     * The URL of the Knative broker (sink).
     */
    @Value("${K_SINK:http://kourier.impl.nl}")
    private String sink;

    /**
     * The source of the events.
     */
    @Value("${spring.application.name:cardserver-api}")
    private String source;
}
