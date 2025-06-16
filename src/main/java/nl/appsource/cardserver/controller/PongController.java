package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.filter.LoggingFilter;
import org.openapitools.api.PongApi;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class PongController implements PongApi {
    @Override
    public ResponseEntity<Void> pong() {
        LoggingFilter.requestLogMessage("pong");
        return ResponseEntity.ok().build();
    }
}
