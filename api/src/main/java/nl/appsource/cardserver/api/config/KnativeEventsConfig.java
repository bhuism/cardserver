package nl.appsource.cardserver.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(nl.appsource.cardserver.openapi.config.KnativeEventsConfig.class)
public class KnativeEventsConfig {

}
