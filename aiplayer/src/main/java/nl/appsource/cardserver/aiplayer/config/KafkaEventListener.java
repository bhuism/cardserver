package nl.appsource.cardserver.aiplayer.config;

import nl.appsource.cardserver.couchbase.utils.GameEngine;
import reactor.core.publisher.Sinks;

public interface KafkaEventListener {

    Sinks.Many<GameEngine> getQueue();

}
