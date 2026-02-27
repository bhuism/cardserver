package nl.appsource.cardserver.service;

import nl.appsource.cardserver.couchbase.model.AiRisc;
import nl.appsource.cardserver.couchbase.model.Boom;
import nl.appsource.cardserver.couchbase.model.GameVariant;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface BoomService {

    Mono<Boom> getBoom(String userId, String boomId);

    Mono<Boom> createBoom(String userI, List<String> players, GameVariant gameVariant, AiRisc aiRisc);

    Flux<String> getBooms(String userId);
}
