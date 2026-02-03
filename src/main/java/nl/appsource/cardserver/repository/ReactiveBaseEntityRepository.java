package nl.appsource.cardserver.repository;

import nl.appsource.cardserver.model.BaseEntity;
import reactor.core.publisher.Mono;

public interface ReactiveBaseEntityRepository<T extends BaseEntity> {

    Mono<String> updateUpdated(String id);

}
