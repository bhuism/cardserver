package nl.appsource.cardserver.service;

import nl.appsource.cardserver.model.Boom;
import org.openapitools.model.GameVariant;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface BoomService {

    Mono<Boom> getBoom(String userId, String boomId);

    Mono<Boom> createBoom(String userI, List<String> players, GameVariant gameVariant);

    Flux<String> getBooms(String userId);
}
