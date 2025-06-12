package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.service.MessageEngine;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;


@Slf4j
@Controller
@RequiredArgsConstructor
public class SSEController {

    private final MessageEngine messageEngine;

    @GetMapping("/websocket")
    public SseEmitter streamSseEvents() throws IOException {
        return messageEngine.subscribe();
    }
}

