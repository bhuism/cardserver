package nl.appsource.cardserver.repository;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.Game;
import org.reactivestreams.Publisher;
import org.springframework.data.couchbase.core.mapping.event.ReactiveBeforeConvertCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Component
public class BeforeSaveEventGame implements ReactiveBeforeConvertCallback<Game> {
    @Override
    public Publisher<Game> onBeforeConvert(final Game game, final String collection) {
        log.info("Before save: " + game.getId());
        game.setUpdated(Instant.now());

        return Mono.just(game);
    }

}
