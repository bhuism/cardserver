package nl.appsource.cardserver.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.mapping.Expiry;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Document
@Getter
@Setter
@ToString
@Expiry(expiry = 30, expiryUnit = TimeUnit.SECONDS)
public class SseEvent extends BaseEntity {

    private final String appIdentifier;

    private final String userId;

    private final String event;

    private final Map<?, ?> data;

    public SseEvent(final String id, final String appIdentifier, final String userId, final String event, final Map<?, ?> data) {
        super(id);
        this.appIdentifier = appIdentifier;
        this.userId = userId;
        this.event = event;
        this.data = data;
    }

}
