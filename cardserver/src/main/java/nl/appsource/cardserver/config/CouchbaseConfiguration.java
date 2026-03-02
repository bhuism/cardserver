package nl.appsource.cardserver.config;

import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Profile( "!citest")
@Import( nl.appsource.cardserver.couchbase.config.CouchbaseConfiguration.class)
public class CouchbaseConfiguration {
}
