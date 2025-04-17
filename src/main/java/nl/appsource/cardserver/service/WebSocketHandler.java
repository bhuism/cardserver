package nl.appsource.cardserver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@Slf4j
public class WebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void handleTransportError(final WebSocketSession session, final Throwable throwable) {
        log.error("error occured at sender " + session, throwable);
    }

    @Override
    public void afterConnectionClosed(final WebSocketSession session, final CloseStatus status) {
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
        final String request = message.getPayload();

        final JSONObject requestJSON = new JSONObject(request);

        log.info("Server #session: {}, received: {}", sessions.size(), requestJSON);

//        final String response = String.format("response from server to '%s'", HtmlUtils.htmlUnescape(request));

//        log.info("Server sends: {}", response);

        final JSONObject payload = new JSONObject();

        payload.put("response", UUID.randomUUID().toString());

        session.sendMessage(new TextMessage(payload.toString()));
    }

}
