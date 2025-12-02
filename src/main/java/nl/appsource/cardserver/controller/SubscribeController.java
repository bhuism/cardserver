package nl.appsource.cardserver.controller;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.repository.UserRepository;
import nl.appsource.cardserver.service.MyServerSentEvent;
import nl.appsource.cardserver.service.SseEmitterRepository;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import java.util.UUID;


@Slf4j
@RestController
public class SubscribeController extends GenericController implements V1Api {

    public static final String APP_IDENTIFIER_HEADER_NAME = "App-Identifier";

    public SubscribeController(final SseEmitterRepository sseEmitterRepository, final UserRepository userRepositoryArg) {
        super(sseEmitterRepository, userRepositoryArg);
    }

    @GetMapping(path = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<@NonNull ServerSentEvent<@NonNull Object>> subscribe(final ServerWebExchange exchange, @RequestHeader(name = APP_IDENTIFIER_HEADER_NAME) final String appIdentifier) {
        return getUserId(exchange).flatMapMany(user -> sseEmitterRepository.subscribe(UUID.fromString(appIdentifier), user.getId(), "" + exchange.getRequest().getRemoteAddress()).map(MyServerSentEvent::getServerSentEvent));
    }

}
