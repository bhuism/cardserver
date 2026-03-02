package nl.appsource.cardserver.stream.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(nl.appsource.cardserver.converters.config.ConvertersConfig.class)
public class ConvertersConfig {
}
