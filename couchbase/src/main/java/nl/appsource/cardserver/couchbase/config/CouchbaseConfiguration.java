package nl.appsource.cardserver.couchbase.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Role;
import org.springframework.data.couchbase.core.convert.translation.JacksonTranslationService;
import org.springframework.data.couchbase.core.convert.translation.TranslationService;
import org.springframework.data.couchbase.repository.auditing.EnableReactiveCouchbaseAuditing;
import org.springframework.data.couchbase.repository.config.EnableReactiveCouchbaseRepositories;

import static org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE;

@Profile("!citest")
@EnableReactiveCouchbaseRepositories(basePackages = "nl.appsource.cardserver.couchbase.repository")
@EnableReactiveCouchbaseAuditing(auditorAwareRef = "reactiveAuditorAware")
@Role(ROLE_INFRASTRUCTURE)
@Configuration
public class CouchbaseConfiguration {

    @Bean
    public TranslationService couchbaseTranslationService() {
        ObjectMapper objectMapper = new ObjectMapper();

        // Register the module for java.time.* classes
        objectMapper.registerModule(new JavaTimeModule());

        // Recommended: store dates as ISO-8601 strings rather than array/timestamp formats
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        JacksonTranslationService translationService = new JacksonTranslationService();
        translationService.setObjectMapper(objectMapper);
        translationService.afterPropertiesSet();

        return translationService;
    }

}
