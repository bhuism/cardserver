package nl.appsource.cardserver.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;

import java.util.UUID;

@Getter
@RequiredArgsConstructor
public final class UserServerSentEvent {
    private final UUID uuid;
    private final ServerSentEvent<Object> serverSentEvent;
}
