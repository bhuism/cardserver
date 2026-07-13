package nl.appsource.cardserver.openapi.service;

import nl.appsource.generated.openapi.model.GameEvent;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

public interface KnativeEventPublisher {
    Mono<ResponseEntity<Void>> publish(GameEvent gameEvent);
}
