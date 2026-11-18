package com.pedidos360.analytics.model;

import java.time.Instant;

public record PaymentAttemptEvent(String eventId, String userId, String orderId,
                                  String status, String reason, Instant occurredAt) {}
