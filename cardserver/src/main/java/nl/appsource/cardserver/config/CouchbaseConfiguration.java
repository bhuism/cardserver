package nl.appsource.cardserver.config;

import com.couchbase.client.java.env.ClusterEnvironment;
import com.couchbase.client.java.query.QueryScanConsistency;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.convert.CustomConversions;
import org.springframework.data.couchbase.config.AbstractCouchbaseConfiguration;
import org.springframework.data.couchbase.config.BeanNames;
import org.springframework.data.couchbase.core.mapping.CouchbaseMappingContext;
import org.springframework.data.couchbase.repository.auditing.EnableReactiveCouchbaseAuditing;
import org.springframework.data.domain.ReactiveAuditorAware;

import java.time.Duration;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;


@Configuration
@RequiredArgsConstructor
@Profile("!citest")
@Slf4j
//@EnableReactiveCouchbaseRepositories(basePackages = "nl.appsource.cardserver.repository")
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
        return QueryScanConsistency.NOT_BOUNDED;
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
    protected void configureEnvironment(final ClusterEnvironment.Builder builder2) {

        // Configure Threshold Logging
        builder2.thresholdLoggingTracerConfig(builder -> builder
            .emitInterval(Duration.ofSeconds(10)) // Log slow ops every 10 seconds
            .sampleSize(10)                       // Log top 10 slowest queries per interval
            .kvThreshold(Duration.ofMillis(500))  // Threshold for Key-Value ops (get/upsert)
            .queryThreshold(Duration.ofSeconds(1)) // Threshold for N1QL queries
            .searchThreshold(Duration.ofSeconds(1)) // Threshold for FTS
            .analyticsThreshold(Duration.ofSeconds(1))
        );

        // Optional: Configure Orphan Reporter (logs requests that failed due to timeout)
        // This helps detect queries that were so slow they never completed.
        builder2.orphanReporterConfig(builder ->
            builder
                .emitInterval(Duration.ofSeconds(10))
                .sampleSize(10)
        );
    }

    @Override
    protected ObjectMapper couchbaseObjectMapper() {
        final ObjectMapper objectMapper = super.couchbaseObjectMapper();

        objectMapper.registerModule(new JavaTimeModule());

        // Standard recommended configurations for Java Time types
        objectMapper.disable(WRITE_DATES_AS_TIMESTAMPS);

        return objectMapper;
    }

    @Override
    @Bean(name = BeanNames.COUCHBASE_MAPPING_CONTEXT)
    public CouchbaseMappingContext couchbaseMappingContext(@Qualifier(BeanNames.COUCHBASE_CUSTOM_CONVERSIONS) final CustomConversions customConversions) throws Exception {
        return super.couchbaseMappingContext(customConversions);
    }

    //    @Bean
//    public ClusterEnvironmentBuilderCustomizer couchbaseEnvironmentCustomizer(JsonMapper jsonMapper) {
//        log.info("Customizing Couchbase environment with JacksonJsonSerializer");
//        return builder -> builder.jsonSerializer(biJacksonJsonSerializer.create(jsonMapper));
//    }
//    @Override
//    public CouchbaseTransactionalOperator couchbaseTransactionalOperator(final CouchbaseCallbackTransactionManager couchbaseCallbackTransactionManager) {
//        return super.couchbaseTransactionalOperator(couchbaseCallbackTransactionManager);
//    }

}

