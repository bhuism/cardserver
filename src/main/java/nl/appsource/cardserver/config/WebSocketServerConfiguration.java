package nl.appsource.cardserver.config;

import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.service.WebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketServerConfiguration implements WebSocketConfigurer {

    private final WebSocketHandler webSocketHandler;

    @Override
    public void registerWebSocketHandlers(final WebSocketHandlerRegistry registry) {
        registry
            .addHandler(webSocketHandler, "/websocket")
            .addInterceptors(new HttpSessionHandshakeInterceptor())
            .setAllowedOriginPatterns("http://localhost:5173/");
    }

}
