package nl.appsource.cardserver.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Role;

import static org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE;

@Configuration
@Role(ROLE_INFRASTRUCTURE)
@Import(nl.appsource.cardserver.couchbase.config.CouchbaseConfiguration.class)
@Profile("!citest")
public class CouchbaseConfiguration {
}
