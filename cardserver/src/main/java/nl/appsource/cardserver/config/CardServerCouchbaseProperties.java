package nl.appsource.cardserver.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Role;
import org.springframework.stereotype.Component;

import static org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE;

@Component
@ConfigurationProperties(prefix = "cardserver.couchbase")
@Getter
@Setter
@RequiredArgsConstructor
@Accessors(chain = true)
@Role(ROLE_INFRASTRUCTURE)
public final class CardServerCouchbaseProperties {

    private String connectionString;
    private String username;
    private String password;
    private String bucketName;

}

