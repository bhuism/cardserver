package nl.appsource.cardserver.api.controller;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.message.MessageReader;
import io.cloudevents.core.message.MessageWriter;
import io.cloudevents.http.HttpMessageFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
public class KnativeEventProcessor {

    @PostMapping(value = "/processOrder")
    public Mono<ResponseEntity<byte[]>> processOrder(
        final @RequestHeader MultiValueMap<String, String> headers,
        final @RequestBody(required = false) byte[] body) {

        return Mono.fromCallable(() -> {
            final byte[] payload = body == null ? new byte[0] : body;

            final Map<String, List<String>> headersMap = new HashMap<>();

            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                headersMap.put(entry.getKey(), entry.getValue());
            }

            final MessageReader reader = HttpMessageFactory.createReaderFromMultimap(headersMap, payload);
            final CloudEvent incomingEvent = reader.toEvent();

            log.info("processOrder bean created");
            log.info("Processing incoming Knative event type: {}", incomingEvent.getType());

            final CloudEvent outgoingEvent = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("https://spring-boot.my-cluster.local"))
                .withType("order.processed")
                .withDataContentType("application/json")
                .withData("{\"status\":\"success\"}".getBytes(StandardCharsets.UTF_8))
                .build();

            final ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok();
            final byte[][] responseBody = new byte[1][1];

            final MessageWriter<?, ?> writer = HttpMessageFactory.createWriter(
                (k, v) -> responseBuilder.header(k, v),
                b -> responseBody[0] = b
            );

            writer.writeBinary(outgoingEvent);

            return responseBuilder.body(responseBody[0]);
        });
    }
}
