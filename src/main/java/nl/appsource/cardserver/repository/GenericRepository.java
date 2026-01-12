package nl.appsource.cardserver.repository;

import nl.appsource.cardserver.model.BaseEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface GenericRepository<T extends BaseEntity, ID> extends ReactiveCrudRepository<T, ID> {

    default Mono<T> updatedSave(T row) {
        row.setUpdated(Instant.now());
        return save(row);
    }

}
