package nl.appsource.cardserver.config;

import com.couchbase.client.core.env.OrphanReporterConfig;
import com.couchbase.client.core.env.ThresholdLoggingTracerConfig;
import com.couchbase.client.java.env.ClusterEnvironment;
import com.couchbase.client.java.query.QueryScanConsistency;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.couchbase.config.AbstractCouchbaseConfiguration;
import org.springframework.data.couchbase.repository.auditing.EnableReactiveCouchbaseAuditing;
import org.springframework.data.couchbase.repository.config.EnableReactiveCouchbaseRepositories;
import org.springframework.data.domain.ReactiveAuditorAware;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;


@Configuration
@RequiredArgsConstructor
@Profile("!citest")
@EnableReactiveCouchbaseRepositories(basePackages = "nl.appsource.cardserver.repository")
@EnableReactiveCouchbaseAuditing(auditorAwareRef = "reactiveAuditorAware")
public class CouchbaseConfiguration extends AbstractCouchbaseConfiguration {

    private final CardServerCouchbaseProperties cardServerCouchbaseProperties;

    private final JsonMapper jsonMapper;

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
        return QueryScanConsistency.NOT_BOUNDED;
    }

    @Bean
    public ReactiveAuditorAware<String> reactiveAuditorAware() {
        return new ReactiveAuditorAwareImpl();
    }

    @Override
    protected void configureEnvironment(final ClusterEnvironment.Builder builder) {

        // Configure Threshold Logging
        builder.thresholdLoggingTracerConfig(ThresholdLoggingTracerConfig.builder()
            .emitInterval(Duration.ofSeconds(10)) // Log slow ops every 10 seconds
            .sampleSize(10)                       // Log top 10 slowest queries per interval
            .kvThreshold(Duration.ofMillis(500))  // Threshold for Key-Value ops (get/upsert)
            .queryThreshold(Duration.ofSeconds(1)) // Threshold for N1QL queries
            .searchThreshold(Duration.ofSeconds(1)) // Threshold for FTS
            .analyticsThreshold(Duration.ofSeconds(1))
        );

        // Optional: Configure Orphan Reporter (logs requests that failed due to timeout)
        // This helps detect queries that were so slow they never completed.
        builder.orphanReporterConfig(
            OrphanReporterConfig.builder()
                .emitInterval(Duration.ofSeconds(10))
                .sampleSize(10)
        );
    }

//    @Override
//    public CouchbaseTransactionalOperator couchbaseTransactionalOperator(final CouchbaseCallbackTransactionManager couchbaseCallbackTransactionManager) {
//        return super.couchbaseTransactionalOperator(couchbaseCallbackTransactionManager);
//    }

}

