package nl.appsource.cardserver.config;

import nl.appsource.cardserver.repository.UserRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.couchbase.config.AbstractCouchbaseConfiguration;
import org.springframework.data.couchbase.repository.config.EnableCouchbaseRepositories;


@Configuration
@EnableCouchbaseRepositories(basePackageClasses = {UserRepository.class})
public class CouchbaseConfiguration extends AbstractCouchbaseConfiguration {

    @Override
    public String getConnectionString() {
        return "couchbase://192.168.1.86";
    }

    @Override
    public String getUserName() {
        return "root";
    }

    @Override
    public String getPassword() {
        return "killer";
    }

    @Override
    public String getBucketName() {
        return "cardserver";
    }

}

