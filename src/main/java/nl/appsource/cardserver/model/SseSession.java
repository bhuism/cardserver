package nl.appsource.cardserver.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.index.QueryIndexed;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.mapping.Expiry;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Document
@Getter
@Setter
@RequiredArgsConstructor
@Expiry(expiry = 30, expiryUnit = TimeUnit.SECONDS)
public class SseSession {

    @Id
    private final UUID appIdentifier;

    @QueryIndexed
    private final String userId;

    private final String remoteAddress;

    private final String userAgent;

    private final Instant created;

    private Instant pingReceived;

    private Instant pongReceived;

    private int pingReceivedCount = 0;

    private int pongReceivedCount = 0;

    public void ping() {
        pingReceived = Instant.now();
        pingReceivedCount++;
    }

    public void pong() {
        pongReceived = Instant.now();
        pongReceivedCount++;
    }

}
