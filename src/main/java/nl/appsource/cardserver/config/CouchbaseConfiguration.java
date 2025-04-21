package nl.appsource.cardserver.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.couchbase.config.AbstractCouchbaseConfiguration;


@Configuration
@RequiredArgsConstructor
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

}

