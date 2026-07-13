package nl.appsource.cardserver.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;

import java.util.Optional;

@Slf4j
@AutoConfiguration
public class GitHashAutoConfiguration {

    @Bean
    public ApplicationListener<ApplicationReadyEvent> gitHashLogger(final Optional<GitProperties> gitProperties) {
        return event -> gitProperties.ifPresentOrElse(
            props -> log.info("Git hash: {}", props.getShortCommitId()),
            () -> log.info("Git hash: not available")
        );
    }

}
