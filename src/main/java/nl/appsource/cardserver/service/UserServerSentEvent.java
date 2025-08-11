package nl.appsource.cardserver.service;

import org.springframework.http.codec.ServerSentEvent;

public record UserServerSentEvent(ServerSentEvent<Object> serverSentEvent) {
}
