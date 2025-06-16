package nl.appsource.cardserver.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.filter.LoggingFilter;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageEngine {

    private final SseEmitterRepository sseEmitterRepository;

    private final UserRepository userRepository;

    public void message(final String source, final String message) {
        final String fromString = userRepository.findById(source).map(User::getDisplayName).orElseThrow(IllegalArgumentException::new);
        sseEmitterRepository.send(fromString, message);
    }

    public SseEmitter subscribe(final String userId) {

        try {
            return sseEmitterRepository.subscribe(userId);
        } finally {
            LoggingFilter.requestLogMessage(", sseEmitterRepository.size()=" + sseEmitterRepository.size());
        }

    }

}
