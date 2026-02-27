package nl.appsource.cardserver.stream.service;

import lombok.NonNull;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface SseEmitterRepository {

    void sendAppIdentifier(String appIdentifier, nl.appsource.cardserver.openapi.MyServerSentEvent myServerSentEvent);

    Flux<@NonNull ServerSentEvent<Object>> subscribe(String userId, String remoteAddress, String userAgent);

}
