package nl.appsource.cardserver.service;

import lombok.NonNull;
import org.springframework.http.codec.ServerSentEvent;

import java.io.Serializable;

public record MyServerSentEvent(ServerSentEvent<@NonNull Object> serverSentEvent) implements Serializable {
}
