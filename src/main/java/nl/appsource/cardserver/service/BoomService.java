package nl.appsource.cardserver.service;

import nl.appsource.cardserver.model.Boom;
import reactor.core.publisher.Mono;

import java.util.List;

public interface BoomService {

    Mono<Boom> getBoom(String userId, String boomId);

    Mono<Boom> createBoom(String userI, List<String> playser);

}
