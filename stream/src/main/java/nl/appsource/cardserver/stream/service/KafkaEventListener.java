package nl.appsource.cardserver.stream.service;

import nl.appsource.cardserver.openapi.MyServerSentEvent;
import nl.appsource.generated.openapi.model.Game;
import org.reactivestreams.Publisher;

public interface KafkaEventListener {
    Publisher<MyServerSentEvent<Game>> gamesChanged(String userId);
}
