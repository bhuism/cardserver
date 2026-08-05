package nl.appsource.cardserver.aiplayer.config;

import nl.appsource.cardserver.couchbase.utils.GameEngine;
import reactor.core.publisher.Flux;

public interface KafkaEventListener {

    Flux<GameEngine> listen();

}
