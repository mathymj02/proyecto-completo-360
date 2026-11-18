package com.examen.catalogoapi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Marca una orden cuyo descuento de stock ya se confirmó en SQL.
 * RabbitMQ puede reenviar un mensaje si el consumidor terminó en SQL pero
 * no alcanzó a confirmar la entrega; la misma orden no debe descontar dos veces.
 */
@Entity
@Table(name = "processed_stock_orders")
public class ProcessedStockOrder {
    @Id
    @Column(length = 64)
    private String orderId;

    @Column(nullable = false)
    private Instant processedAt;

    protected ProcessedStockOrder() { }

    public ProcessedStockOrder(String orderId) {
        this.orderId = orderId;
        this.processedAt = Instant.now();
    }

    public String getOrderId() { return orderId; }
    public Instant getProcessedAt() { return processedAt; }
}
