package nl.appsource.cardserver.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.index.QueryIndexed;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.mapping.Expiry;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Document
@Getter
@Setter
@Expiry(expiry = 30, expiryUnit = TimeUnit.SECONDS)
public class SseSession {

    @Id
    private String id;

    private final String remoteAddress;

    private final String userAgent;

    private final String host;

    private Instant pingReceived;

    private Instant pongReceived;

    private int pingReceivedCount = 0;

    private int pongReceivedCount = 0;

    private Instant created = Instant.now();

    private Instant updated = Instant.now();

    @QueryIndexed
    private String creator;

    public SseSession(final String id, final String remoteAddress, final String userAgent, final String host, final String creator) {
        this.id = id;
        this.remoteAddress = remoteAddress;
        this.userAgent = userAgent;
        this.host = host;
        this.creator = creator;
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
