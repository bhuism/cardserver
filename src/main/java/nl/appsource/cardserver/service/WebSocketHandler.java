package nl.appsource.cardserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handleTransportError(final WebSocketSession session, final Throwable throwable) {
        log.error("Error occured at sender " + session);
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
        session.sendMessage(new TextMessage("syn"));
    }

    @Override
    protected void handlePongMessage(final WebSocketSession session, final PongMessage message) throws Exception {
        super.handlePongMessage(session, message);
        log.info("Got Pong message " + message.getPayload());
    }

    @Override
    protected void handleTextMessage(final WebSocketSession session, final TextMessage message) throws Exception {
        final String request = message.getPayload();

//        log.info("Server session: {},  #session: {}, payload: {}", session.getId(), sessions.size(), request);

        if ("syn".equals(request)) {
            session.sendMessage(new TextMessage("ack"));
        } else if ("ack".equals(request)) {
            log.trace("ack");
        } else {


            log.info("ws: received  {}", request);
            // final JsonNode jsonNode = objectMapper.readTree(request);

            // log.info("json: {}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode));

            final ObjectNode rootNode = objectMapper.createObjectNode();
            rootNode.put("key", UUID.randomUUID().toString());

            log.info("ws: sending: {}", objectMapper.writeValueAsString(rootNode));

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(rootNode)));
        }
    }

}
