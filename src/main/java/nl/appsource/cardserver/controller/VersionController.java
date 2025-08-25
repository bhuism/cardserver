package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.info.GitProperties;
import org.springframework.boot.info.InfoProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@CrossOrigin
@Controller
@RequiredArgsConstructor
public class VersionController {

    private final GitProperties gitProperties;

    @GetMapping(value = "/version", produces = "application/json")
    public ResponseEntity<Map<String, String>> getVersion() {
        final Map<String, String> properties = StreamSupport.stream(gitProperties.spliterator(), false)
            .collect(Collectors.toMap(InfoProperties.Entry::getKey, InfoProperties.Entry::getValue));

        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
            .body(properties);
    }

}
