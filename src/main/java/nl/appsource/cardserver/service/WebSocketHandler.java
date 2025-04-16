package nl.appsource.cardserver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.HtmlUtils;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@Slf4j
public class WebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void handleTransportError(final WebSocketSession session, final Throwable throwable) throws Exception {
        log.error("error occured at sender " + session, throwable);
    }

    @Override
    public void afterConnectionClosed(final WebSocketSession session, final CloseStatus status) throws Exception {
        log.info(String.format("Session %s closed because of %s", session.getId(), status.getReason()));
        sessions.remove(session);
    }

    @Override
    public void afterConnectionEstablished(final WebSocketSession session) throws Exception {
        log.info("Connected return null; " + session.getId());
        sessions.add(session);
        TextMessage message = new TextMessage("one-time message from server");
        log.info("Server sends: {}", message);
        session.sendMessage(message);
    }

    @Override
    protected void handleTextMessage(final WebSocketSession session, final TextMessage message) throws Exception {
        String request = message.getPayload();
        log.info("Server received: {}", request);

        String response = String.format("response from server to '%s'", HtmlUtils.htmlUnescape(request));
        log.info("Server sends: {}", response);
        session.sendMessage(new TextMessage(response));
    }

}
