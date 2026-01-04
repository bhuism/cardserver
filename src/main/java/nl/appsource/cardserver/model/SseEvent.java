package nl.appsource.cardserver.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.mapping.Expiry;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Document
@Getter
@Setter
@ToString
@AllArgsConstructor
@Expiry(expiry = 30, expiryUnit = TimeUnit.SECONDS)
public class SseEvent {

    @Id
    private String id;

    private final String appIdentifier;

    private final String userId;

    private final String event;

    private final Map<?, ?> data;

}
