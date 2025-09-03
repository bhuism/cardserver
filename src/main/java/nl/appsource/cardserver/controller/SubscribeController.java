package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import java.util.UUID;


@Slf4j
@RestController
@RequiredArgsConstructor
public class SubscribeController implements V1Api {

    public static final String APP_IDENTIFIER_HEADER_NAME = "App-Identifier";

    private final UserService userService;

    @GetMapping(path = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<?>> subscribe(final ServerWebExchange exchange, @RequestHeader(name = APP_IDENTIFIER_HEADER_NAME) final String appIdentifier) {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .flatMapMany(userId -> userService.subscribe(UUID.fromString(appIdentifier), userId, "" + exchange.getRequest().getRemoteAddress()));
    }

}
