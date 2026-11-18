package com.pedidos360.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.analytics.model.InteractionEvent;
import com.pedidos360.analytics.model.PaymentAttemptEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MetricsService {
    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);
    private final AtomicLong productViews = new AtomicLong();
    private final AtomicLong cartAdds = new AtomicLong();
    private final AtomicLong paymentAttempts = new AtomicLong();
    private final AtomicLong fraudAlerts = new AtomicLong();
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final RecommendationEngine recommendations;
    private final ObjectMapper mapper;

    public MetricsService(RecommendationEngine recommendations, ObjectMapper mapper) {
        this.recommendations = recommendations;
        this.mapper = mapper;
    }

    public record Snapshot(long productViews, long cartAdds, long paymentAttempts,
                           long fraudAlerts, java.util.List<RecommendationEngine.Recommendation> topProducts) {}

    public Snapshot snapshot() {
        return new Snapshot(productViews.get(), cartAdds.get(), paymentAttempts.get(),
                fraudAlerts.get(), recommendations.topProducts());
    }

    public void record(InteractionEvent event) {
        if ("VIEW_PRODUCT".equals(event.type())) productViews.incrementAndGet();
        if ("ADD_TO_CART".equals(event.type())) cartAdds.incrementAndGet();
        broadcast();
    }

    public void record(PaymentAttemptEvent event) {
        if ("INITIATED".equals(event.status()) || "BLOCKED".equals(event.status())) {
            paymentAttempts.incrementAndGet();
        }
        if ("BLOCKED".equals(event.status())) fraudAlerts.incrementAndGet();
        broadcast();
    }

    public void connect(WebSocketSession session) throws IOException {
        sessions.add(session);
        send(session);
    }

    public void disconnect(WebSocketSession session) {
        sessions.remove(session);
    }

    private void broadcast() {
        for (WebSocketSession session : sessions) {
            try {
                send(session);
            } catch (IOException | IllegalStateException e) {
                sessions.remove(session);
                log.warn("Se desconecto un cliente de metricas: {}", e.getMessage());
            }
        }
    }

    private void send(WebSocketSession session) throws IOException {
        if (!session.isOpen()) return;
        synchronized (session) {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(snapshot())));
        }
    }
}
