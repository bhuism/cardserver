package nl.appsource.cardserver.aiplayer.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Role;

@Configuration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@Import({nl.appsource.cardserver.couchbase.config.CouchbaseConfiguration.class, nl.appsource.cardserver.couchbase.config.CardServerCouchbaseProperties.class})
@Profile("!citest")
public class CouchbaseConfiguration {
}
