package nl.appsource.cardserver.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.couchbase.core.index.QueryIndexed;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.mapping.Expiry;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Document
@Getter
@Setter
@Expiry(expiry = 30, expiryUnit = TimeUnit.SECONDS)
public class SseSession extends BaseEntity {

    private final String remoteAddress;

    private final String userAgent;

    @QueryIndexed
    private final String host;

    private Instant pingReceived;

    private Instant pongReceived;

    private int pingReceivedCount = 0;

    private int pongReceivedCount = 0;

    public SseSession(final String id, final String remoteAddress, final String userAgent, final String host, final String creator) {
        setId(id);
        this.remoteAddress = remoteAddress;
        this.userAgent = userAgent;
        this.host = host;
        this.setCreator(creator);
    }

    public void ping() {
        pingReceived = Instant.now();
        pingReceivedCount++;
    }

    public void pong() {
        pongReceived = Instant.now();
        pongReceivedCount++;
    }

}
