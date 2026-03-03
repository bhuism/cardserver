package nl.appsource.cardserver.stream.service;

import lombok.NonNull;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface SseEmitterRepository {

    Flux<@NonNull ServerSentEvent<Object>> subscribe(String userId, String remoteAddress, String userAgent);

}
