package nl.appsource.cardsever.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Profile("!citest")
@Import({nl.appsource.cardserver.couchbase.config.CouchbaseConfiguration.class, nl.appsource.cardserver.couchbase.config.CardServerCouchbaseProperties.class})
@Configuration
public class CouchbaseConfiguration {
}
