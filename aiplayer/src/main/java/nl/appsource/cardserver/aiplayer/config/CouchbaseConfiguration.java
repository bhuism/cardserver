package nl.appsource.cardserver.aiplayer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Configuration
@Import({nl.appsource.cardserver.couchbase.config.CouchbaseConfiguration.class, nl.appsource.cardserver.couchbase.config.CardServerCouchbaseProperties.class})
@Profile("!citest")
public class CouchbaseConfiguration {
}
