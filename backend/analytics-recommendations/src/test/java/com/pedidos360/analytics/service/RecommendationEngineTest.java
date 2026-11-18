package com.pedidos360.analytics.service;

import com.pedidos360.analytics.model.InteractionEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationEngineTest {
    @Test
    void recommendsOtherViewedProductInPreferredCategory() {
        RecommendationEngine engine = new RecommendationEngine();
        engine.record(event("1", "ana", 1L, "Monitor A", "Monitores"));
        engine.record(event("2", "bob", 2L, "Monitor B", "Monitores"));
        engine.record(event("3", "bob", 3L, "Teclado", "Perifericos"));

        var recommendations = engine.recommend("ana");
        assertEquals(1, recommendations.size());
        assertEquals(2L, recommendations.get(0).productId());
        assertEquals("Monitor B", recommendations.get(0).name());
    }

    private InteractionEvent event(String id, String user, Long product, String name, String category) {
        return new InteractionEvent(id, user, "VIEW_PRODUCT", product, name, category, 1, Instant.now());
    }
}
