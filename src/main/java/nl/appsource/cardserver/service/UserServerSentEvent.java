package nl.appsource.cardserver.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;

@Getter
@RequiredArgsConstructor
public final class UserServerSentEvent {
    private final ServerSentEvent<Object> serverSentEvent;
}
