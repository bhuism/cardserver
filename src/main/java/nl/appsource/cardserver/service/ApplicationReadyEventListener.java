package nl.appsource.cardserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.migrator.Migrator;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationReadyEventListener implements ApplicationListener<ApplicationReadyEvent> {

    private final Migrator migrator;

    private Environment environment;

    @Override
    @Transactional
    public void onApplicationEvent(final ApplicationReadyEvent event) {
        log.info(event.getClass()
            .getSimpleName());

        if (environment.acceptsProfiles(Profiles.of("production"))) {
            migrator.run();
        }
    }

}
