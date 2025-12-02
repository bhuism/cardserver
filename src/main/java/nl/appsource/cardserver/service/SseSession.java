package nl.appsource.cardserver.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class SseSession {

    private final UUID appIdentifier;

    private final String userId;

    private final Instant created = Instant.now();

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
