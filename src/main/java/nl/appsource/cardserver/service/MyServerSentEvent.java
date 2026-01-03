package nl.appsource.cardserver.service;

import lombok.NonNull;
import org.springframework.http.codec.ServerSentEvent;

import java.io.Serializable;

public record MyServerSentEvent(String appIdentifier, String userId, ServerSentEvent<@NonNull Object> serverSentEvent) implements Serializable {
}
