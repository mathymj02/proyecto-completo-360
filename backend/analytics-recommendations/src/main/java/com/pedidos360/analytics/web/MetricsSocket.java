package com.pedidos360.analytics.web;

import com.pedidos360.analytics.service.MetricsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class MetricsSocket extends TextWebSocketHandler {
    private final MetricsService metrics;

    public MetricsSocket(MetricsService metrics) {
        this.metrics = metrics;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        metrics.connect(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        metrics.disconnect(session);
    }

    @Configuration
    @EnableWebSocket
    static class SocketConfig implements WebSocketConfigurer {
        private final MetricsSocket socket;
        private final String origin;

        SocketConfig(MetricsSocket socket, @Value("${analytics.frontend-origin}") String origin) {
            this.socket = socket;
            this.origin = origin;
        }

        @Override
        public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
            registry.addHandler(socket, "/ws/metrics").setAllowedOrigins(origin);
        }
    }
}
