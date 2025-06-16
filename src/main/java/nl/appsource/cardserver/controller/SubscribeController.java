package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.filter.LoggingFilter;
import nl.appsource.cardserver.service.MessageEngine;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE;


@Slf4j
@Controller
@RequiredArgsConstructor
public class SubscribeController {

    private final MessageEngine messageEngine;

    @GetMapping(value = "/subscribe", produces = TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() throws IOException {

//        LoggingFilter.requestLogMessage("subscribe()");

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String userId = authentication.getName();

        LoggingFilter.requestLogMessage("userId=" + userId);

//        SseEmitter sseEmitter = new SseEmitter(Long.MAX_VALUE);
//
//        log.info("sending...");
//        sseEmitter.send("test");
//        sseEmitter.send(SseEmitter.event().name("cardservermessage").id(UUID.randomUUID().toString()).data("Welcome " + userId).build());
//        log.info("send...");
//
//        return sseEmitter;

        return messageEngine.subscribe(userId);
    }
}

