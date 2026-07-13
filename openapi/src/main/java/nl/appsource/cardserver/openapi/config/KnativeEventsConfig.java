package nl.appsource.cardserver.openapi.config;

import nl.appsource.cardserver.openapi.service.KnativeEventPublisherImpl;
import org.springframework.context.annotation.Import;

@Import(KnativeEventPublisherImpl.class)
public class KnativeEventsConfig {

}
