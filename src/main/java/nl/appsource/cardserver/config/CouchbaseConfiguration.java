package nl.appsource.cardserver.config;

import com.couchbase.client.java.query.QueryScanConsistency;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.couchbase.config.AbstractCouchbaseConfiguration;
import org.springframework.data.couchbase.repository.auditing.EnableReactiveCouchbaseAuditing;
import org.springframework.data.couchbase.repository.config.EnableReactiveCouchbaseRepositories;
import org.springframework.data.domain.ReactiveAuditorAware;

import static com.couchbase.client.java.query.QueryScanConsistency.REQUEST_PLUS;


@Configuration
@RequiredArgsConstructor
@Profile("!citest")
@EnableReactiveCouchbaseRepositories(basePackages = "nl.appsource.cardserver.repository")
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
        return REQUEST_PLUS;
    }

    @Bean
    public ReactiveAuditorAware<String> reactiveAuditorAware() {
        return new ReactiveAuditorAwareImpl();
    }
}

