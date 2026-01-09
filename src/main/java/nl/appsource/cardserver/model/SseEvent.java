package nl.appsource.cardserver.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.mapping.Expiry;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Document
@Getter
@Setter
@ToString
@Expiry(expiry = 30, expiryUnit = TimeUnit.SECONDS)
public class SseEvent {

    @Id
    private String id;

    private final String appIdentifier;

    private final String userId;

    private final String event;

    private final Map<?, ?> data;

    @Version
    private Long version;

    public SseEvent(final String id, final String appIdentifier, final String userId, final String event, final Map<?, ?> data) {
        this.id = id;
        this.appIdentifier = appIdentifier;
        this.userId = userId;
        this.event = event;
        this.data = data;
    }

}
