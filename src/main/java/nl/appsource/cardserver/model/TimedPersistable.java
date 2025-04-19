package nl.appsource.cardserver.model;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * TimedPersistable.
 *
 * @param <P> the type of pk
 */

public interface TimedPersistable<P extends Serializable> extends CardPersistable<P> {

    OffsetDateTime getCreated();

    void setCreated(OffsetDateTime created);

    OffsetDateTime getUpdated();

    void setUpdated(OffsetDateTime updated);

    Long getVersion();

    void setVersion(Long version);

}
