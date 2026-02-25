package nl.appsource.cardserver.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "cardserver.auth")
@Getter
@Setter
@RequiredArgsConstructor
@Accessors(chain = true)
public final class CardServerProperties {

    private String jwtEd25519Secret;

    @PostConstruct
    public void validate() {
        if (!StringUtils.hasText(jwtEd25519Secret)) {
            throw new RuntimeException("Please set a jwtSecret");
        }
    }
}

