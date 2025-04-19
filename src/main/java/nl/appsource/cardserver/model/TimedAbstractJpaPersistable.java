package nl.appsource.cardserver.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * TimedAbstractJpaPersistable.
 */

@Slf4j
@Getter
@Setter
@NoArgsConstructor
@MappedSuperclass
public abstract class TimedAbstractJpaPersistable extends JpaAbstractPersistable implements JpaTimedPersistable {

    @Column(nullable = false)
    private OffsetDateTime created;

    @Column(nullable = false)
    private OffsetDateTime updated;

    @Column(nullable = false)
    private Long version;

    public TimedAbstractJpaPersistable(final Long id) {
        super(id);
    }

    @PrePersist
    public void prePersist() {

        // trunc milliseconds precision cause mysql won't save it, otherwise we get inequality with the original when updating
        final OffsetDateTime timestamp = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        setCreated(timestamp);
        setUpdated(timestamp);

        if (getVersion() == null) {
            setVersion(1L);
        }

    }

    @PreUpdate
    public void preUpdate() {

        // trunc milliseconds precision cause mysql won't save it, otherwise we get inequality with the original when updating
        final OffsetDateTime timestamp = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        setUpdated(timestamp);

        setVersion(getVersion() + 1L);
    }

}
