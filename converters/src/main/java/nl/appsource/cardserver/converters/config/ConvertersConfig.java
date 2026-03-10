package nl.appsource.cardserver.converters.config;


import nl.appsource.cardserver.converters.service.BoomToOpenApiConverter;
import nl.appsource.cardserver.converters.service.GameToOpenApiConverter;
import nl.appsource.cardserver.converters.service.UserToOpenApiConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConvertersConfig {

    @Bean
    public BoomToOpenApiConverter boomToOpenApiConverter() {
        return new BoomToOpenApiConverter();
    }

    @Bean
    public GameToOpenApiConverter gameToOpenApiConverter() {
        return new GameToOpenApiConverter();
    }

    @Bean
    public UserToOpenApiConverter userToOpenApiConverter() {
        return new UserToOpenApiConverter();
    }

}
