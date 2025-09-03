package nl.appsource.cardserver.controller;

import org.springframework.boot.info.GitProperties;
import org.springframework.boot.info.InfoProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@CrossOrigin
@Controller
public class VersionController {

    private final Map<String, String> gitPropertiesMap;

    public VersionController(final GitProperties gitProperties) {
        gitPropertiesMap = StreamSupport.stream(gitProperties.spliterator(), false)
            .collect(Collectors.toMap(InfoProperties.Entry::getKey, InfoProperties.Entry::getValue));
    }

    @GetMapping(value = "/version", produces = "application/json")
    public ResponseEntity<Map<String, String>> getVersion() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header("Pragma", "no-cache")
            .header("Expires", "0")
            .body(gitPropertiesMap);
    }

}
