package nl.appsource.cardserver.couchbase.config;

import com.couchbase.client.java.query.QueryScanConsistency;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.couchbase.config.AbstractCouchbaseConfiguration;
import org.springframework.data.couchbase.repository.auditing.EnableReactiveCouchbaseAuditing;
import org.springframework.data.couchbase.repository.config.EnableReactiveCouchbaseRepositories;
import org.springframework.data.domain.ReactiveAuditorAware;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

@RequiredArgsConstructor
@Slf4j
@EnableReactiveCouchbaseRepositories(basePackages = "nl.appsource.cardserver.couchbase.repository")
@EnableReactiveCouchbaseAuditing(auditorAwareRef = "reactiveAuditorAware")
public class CouchbaseConfiguration extends AbstractCouchbaseConfiguration {

    private final CardServerCouchbaseProperties cardServerCouchbaseProperties;

    @Override
    public String getConnectionString() {
        return cardServerCouchbaseProperties.getConnectionString();
    }

    @Override
    public String getUserName() {
        return cardServerCouchbaseProperties.getUsername();
    }

    @Override
    public String getPassword() {
        return cardServerCouchbaseProperties.getPassword();
    }

    @Override
    public String getBucketName() {
        return cardServerCouchbaseProperties.getBucketName();
    }

    @Override
    public QueryScanConsistency getDefaultConsistency() {
        return QueryScanConsistency.REQUEST_PLUS;
    }

    @Override
    protected boolean autoIndexCreation() {
        return true;
    }

    @Bean
    public ReactiveAuditorAware<String> reactiveAuditorAware() {
        return new ReactiveAuditorAwareImpl();
    }

    @Override
    protected ObjectMapper couchbaseObjectMapper() {
        final ObjectMapper objectMapper = super.couchbaseObjectMapper();

        objectMapper.registerModule(new JavaTimeModule());

        // Standard recommended configurations for Java Time types
        objectMapper.disable(WRITE_DATES_AS_TIMESTAMPS);

        return objectMapper;
    }

}

