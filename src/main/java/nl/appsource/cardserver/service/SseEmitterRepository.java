package nl.appsource.cardserver.service;

import lombok.NonNull;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface SseEmitterRepository {

    void send(String appIdentifier, String userId, MyServerSentEvent myServerSentEvent);

    Flux<@NonNull ServerSentEvent<Object>> subscribe(String appIdentifier, String userId, String remoteAddress, String userAgent);


}
