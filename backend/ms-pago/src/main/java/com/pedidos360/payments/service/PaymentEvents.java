package com.pedidos360.payments.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentEvents {
    private static final Logger log = LoggerFactory.getLogger(PaymentEvents.class);
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public record PaymentAttemptEvent(String eventId, String userId, String orderId,
                                      String status, String reason, Instant occurredAt) {}

    public PaymentEvents(KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        this.kafka = kafka;
        this.mapper = mapper;
    }

    public void publish(String userId, String paymentId, String status, String reason) {
        var event = new PaymentAttemptEvent(UUID.randomUUID().toString(), userId,
                paymentId, status, reason, Instant.now());
        try {
            kafka.send("intentos-pago", userId, mapper.writeValueAsString(event))
                    .whenComplete((result, error) -> {
                        if (error != null) log.error("Fallo Kafka para pago {}", paymentId, error);
                    });
        } catch (Exception e) {
            log.error("No se pudo serializar evento de pago {}", paymentId, e);
        }
    }
}
