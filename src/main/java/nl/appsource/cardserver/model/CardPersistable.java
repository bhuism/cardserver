package nl.appsource.cardserver.model;


import java.io.Serializable;

/**
 * Persistable.
 * <p>
 * base interface for all persisted things
 *
 * @param <P> the primary key type
 */

public interface CardPersistable<P extends Serializable> extends org.springframework.data.domain.Persistable<P>, Serializable {

    void setId(P id);

    @Override
    default boolean isNew() {
        return null == getId();
    }

    default boolean isNotNew() {
        return !isNew();
    }

    default void makeNew() {
        setId(null);
    }

}
