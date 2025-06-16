package nl.appsource.cardserver.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.filter.LoggingFilter;
import nl.appsource.cardserver.service.MessageEngine;
import org.openapitools.api.MessageApi;
import org.openapitools.model.PostMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class MessageController implements MessageApi, V1Api {

    private final MessageEngine messageEngine;

    @Override
    public ResponseEntity<Void> sendAMessage(final PostMessage postMessage) {


        LoggingFilter.requestLogMessage("sendAMessage()");

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String userId = authentication.getName();

        messageEngine.message(userId, postMessage.getMessage());


        return ResponseEntity.ok().build();
    }

}
