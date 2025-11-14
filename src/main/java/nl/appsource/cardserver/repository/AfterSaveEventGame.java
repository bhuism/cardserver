package nl.appsource.cardserver.repository;

import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.Game;
import org.springframework.data.couchbase.core.mapping.CouchbaseDocument;
import org.springframework.data.couchbase.core.mapping.event.AfterSaveEvent;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AfterSaveEventGame extends AfterSaveEvent<Game> {

    public AfterSaveEventGame(final Game game, final CouchbaseDocument document) {
        super(game, document);
        log.info("After save: " + game.getId());
    }

}
