package nl.appsource.cardserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Profile("!citest")
@Import(nl.appsource.cardserver.couchbase.config.CouchbaseConfiguration.class)
@Configuration
public class CouchbaseConfiguration {
}
