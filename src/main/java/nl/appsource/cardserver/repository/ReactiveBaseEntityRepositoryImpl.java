package nl.appsource.cardserver.repository;

import com.couchbase.client.core.error.DocumentNotFoundException;
import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.model.BaseEntity;
import org.springframework.data.couchbase.core.ReactiveCouchbaseTemplate;
import reactor.core.publisher.Mono;

import static com.couchbase.client.java.kv.MutateInSpec.upsert;
import static java.util.Collections.singletonList;

@RequiredArgsConstructor
public class ReactiveBaseEntityRepositoryImpl<T extends BaseEntity> implements ReactiveBaseEntityRepository<T> {

    private final ReactiveCouchbaseTemplate template;

    @Override
    public Mono<String> updateUpdated(final String id) {
        return template.getCouchbaseClientFactory()
            .getBucket()
            .defaultCollection()
            .reactive()
            .mutateIn(id, singletonList(upsert("updated", System.currentTimeMillis())))
            .onErrorResume(DocumentNotFoundException.class, ex -> Mono.empty())
            .map(_mutateInResult -> id);
    }

}
