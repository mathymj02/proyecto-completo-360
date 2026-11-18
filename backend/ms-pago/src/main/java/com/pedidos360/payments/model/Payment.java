package com.pedidos360.payments.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    private String id;
    @Column(nullable = false)
    private String userId;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false)
    private String cardFingerprint;
    @Column(nullable = false)
    private String status;
    private String webpayToken;
    @Column(nullable = false)
    private Instant createdAt;

    protected Payment() {}

    public Payment(String id, String userId, BigDecimal amount, String cardFingerprint, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.amount = amount;
        this.cardFingerprint = cardFingerprint;
        this.status = "CREATED";
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getWebpayToken() { return webpayToken; }
    public void setWebpayToken(String webpayToken) { this.webpayToken = webpayToken; }
}
