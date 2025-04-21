package nl.appsource.cardserver.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cardserver.couchbase")
@Getter
@Setter
@RequiredArgsConstructor
@Accessors(chain = true)
public final class CardServerCouchbaseProperties {

    private String connectionString;
    private String username;
    private String password;
    private String bucketName;

}

