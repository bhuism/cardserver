package nl.appsource.cardserver.gameengine.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(nl.appsource.cardserver.converters.config.ConvertersConfig.class)
public class ConverterConfig {
}
