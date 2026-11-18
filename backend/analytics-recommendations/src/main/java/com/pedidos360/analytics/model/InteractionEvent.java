package com.pedidos360.analytics.model;

import java.time.Instant;

public record InteractionEvent(String eventId, String userId, String type,
                               Long productId, String productName, String category,
                               Integer quantity, Instant occurredAt) {}
