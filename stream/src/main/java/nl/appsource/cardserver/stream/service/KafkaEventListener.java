package nl.appsource.cardserver.stream.service;

import nl.appsource.cardserver.openapi.MyServerSentEvent;
import org.reactivestreams.Publisher;

public interface KafkaEventListener {
    Publisher<MyServerSentEvent<?>> kafkaStreams();
}
