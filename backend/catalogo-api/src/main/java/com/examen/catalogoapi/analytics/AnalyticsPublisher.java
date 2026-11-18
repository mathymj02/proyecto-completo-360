package com.examen.catalogoapi.analytics;

import com.examen.catalogoapi.model.Producto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AnalyticsPublisher {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsPublisher.class);
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final boolean enabled;

    public AnalyticsPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                              @Value("${analytics.kafka.enabled:false}") boolean enabled) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.enabled = enabled;
    }

    public void productViewed(String userId, Producto product) {
        if (!enabled) return;
        var event = new InteractionEvent(UUID.randomUUID().toString(), userId,
                "VIEW_PRODUCT", product.getId(), product.getNombre(),
                product.getCategoria(), 1, Instant.now());
        try {
            kafka.send("navegacion-productos", userId, mapper.writeValueAsString(event))
                    .whenComplete((result, error) -> {
                        if (error != null) log.error("No se pudo publicar evento {}", event.eventId(), error);
                    });
        } catch (JsonProcessingException e) {
            log.error("No se pudo serializar evento {}", event.eventId(), e);
        }
    }
}
