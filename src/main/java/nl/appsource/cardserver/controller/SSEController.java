package nl.appsource.cardserver.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


@Slf4j
@Controller
public class SSEController {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @GetMapping("/websocket")
    public SseEmitter streamSseEvents() throws IOException {
        final SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);
//        emitter.send("SSE MVC - " + System.currentTimeMillis());
        emitter.onCompletion(() -> {
            log.info("onCompletion() Removing an emitier");
            emitters.remove(emitter);
        });
        emitter.onTimeout(() -> {
            log.info("onTimeout() Removing an emitier");
            emitters.remove(emitter);
        });
        return emitter;
    }
}

