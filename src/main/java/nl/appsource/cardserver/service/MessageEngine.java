package nl.appsource.cardserver.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.filter.LoggingFilter;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageEngine {

    private final SseEmitterRepository sseEmitterRepository;

    private final UserRepository userRepository;

    public void message(final String userId, final String message) {
        sseEmitterRepository.sendMessage(userId, message);
    }

    public SseEmitter subscribe(final String userId) {

        try {
            return sseEmitterRepository.subscribe(userId);
        } finally {
            LoggingFilter.requestLogMessage(", sseEmitterRepository.size()=" + sseEmitterRepository.size());
        }

    }

    public void ping(final String userId, final UUID uuid) {
        sseEmitterRepository.ping(uuid);
    }

    public void pong(final String userId, final UUID uuid) {
        sseEmitterRepository.pong(uuid);
    }

}
