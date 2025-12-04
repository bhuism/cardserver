package nl.appsource.cardserver.service;

import lombok.NonNull;
import org.springframework.http.codec.ServerSentEvent;

import java.io.Serializable;
import java.util.UUID;

public interface MyServerSentEvent extends Serializable {
    ServerSentEvent<@NonNull Object> getServerSentEvent();

    UUID getAppIdentifier();

    String getUserId();
}
