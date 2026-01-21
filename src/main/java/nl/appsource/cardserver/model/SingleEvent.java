package nl.appsource.cardserver.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.mapping.Expiry;
import org.springframework.data.couchbase.core.mapping.Field;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.springframework.util.StringUtils.hasText;

@Document
@Getter
@Setter
@ToString
@Expiry(expiry = 60, expiryUnit = TimeUnit.SECONDS)
public class SingleEvent {

    @Id
    private String id;

    @CreatedDate
    private Instant created;

    private String lockedBy;

    private String handledBy;

    @Field
    private String event;

    @Version
    private Long version;

    public boolean isUnlocked() {
        return !hasText(this.lockedBy) && !hasText(this.handledBy);
    }

    public boolean isLockedBy(final String lockedByArg) {
        return this.lockedBy.equals(lockedByArg) && !hasText(this.handledBy);
    }

}
