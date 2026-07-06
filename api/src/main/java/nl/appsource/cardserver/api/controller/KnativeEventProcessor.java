package nl.appsource.cardserver.api.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
public class KnativeEventProcessor {

    @PostMapping(value = "/processOrder", consumes = "application/json", produces = "application/json")
    public Map<String, Object> processOrder(@RequestBody final Map<String, Object> incomingEvent) {
        log.info("processOrder bean created");
            log.info("Processing incoming Knative event: {}", incomingEvent.get("type"));
            return Map.of(
                "specversion", "1.0",
                "id", UUID.randomUUID().toString(),
                "source", "https://spring-boot.my-cluster.local",
                "type", "order.processed",
                "datacontenttype", "application/json",
                "data", Map.of("status", "success")
            );
    }

}
