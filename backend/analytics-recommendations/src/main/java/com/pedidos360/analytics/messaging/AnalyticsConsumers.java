package com.pedidos360.analytics.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.analytics.model.InteractionEvent;
import com.pedidos360.analytics.model.PaymentAttemptEvent;
import com.pedidos360.analytics.service.DynamoEventStore;
import com.pedidos360.analytics.service.MetricsService;
import com.pedidos360.analytics.service.RecommendationEngine;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsConsumers {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsConsumers.class);
    private final ObjectMapper mapper;
    private final DynamoEventStore store;
    private final RecommendationEngine recommendations;
    private final MetricsService metrics;

    public AnalyticsConsumers(ObjectMapper mapper, DynamoEventStore store,
                              RecommendationEngine recommendations, MetricsService metrics) {
        this.mapper = mapper;
        this.store = store;
        this.recommendations = recommendations;
        this.metrics = metrics;
    }

    @KafkaListener(topics = "navegacion-productos")
    public void onInteraction(ConsumerRecord<String, String> record) throws Exception {
        InteractionEvent event = mapper.readValue(record.value(), InteractionEvent.class);
        if (event.eventId() == null || event.userId() == null || event.productId() == null
                || event.occurredAt() == null || !("VIEW_PRODUCT".equals(event.type())
                || "ADD_TO_CART".equals(event.type()))) {
            throw new IllegalArgumentException("Evento de navegacion incompleto");
        }
        store.save(event);
        recommendations.record(event);
        metrics.record(event);
        log.info("Evento {} procesado: particion={}, offset={}",
                event.eventId(), record.partition(), record.offset());
    }

    @KafkaListener(topics = "intentos-pago")
    public void onPayment(ConsumerRecord<String, String> record) throws Exception {
        PaymentAttemptEvent event = mapper.readValue(record.value(), PaymentAttemptEvent.class);
        if (event.eventId() == null || event.userId() == null || event.orderId() == null
                || event.status() == null || event.occurredAt() == null) {
            throw new IllegalArgumentException("Evento de pago incompleto");
        }
        store.save(event);
        metrics.record(event);
        log.info("Pago {} procesado: estado={}, particion={}, offset={}",
                event.orderId(), event.status(), record.partition(), record.offset());
    }
}
