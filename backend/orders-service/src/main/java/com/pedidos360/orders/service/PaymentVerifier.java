package com.pedidos360.orders.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Service
public class PaymentVerifier {
    private final boolean required;
    private final RestClient client;

    public record PaymentStatus(String paymentId, String status, BigDecimal amount) {}

    public PaymentVerifier(@Value("${payments.required:false}") boolean required,
                           @Value("${payments.url:http://ms-pago:8087/api/v1/payments}") String paymentsUrl) {
        this.required = required;
        this.client = RestClient.builder().baseUrl(paymentsUrl).build();
    }

    public boolean isAllowed(String paymentId, Double total, String authorization) {
        if (!required) return true;
        if (paymentId == null || paymentId.isBlank() || total == null
                || authorization == null || !authorization.startsWith("Bearer ")) return false;
        try {
            PaymentStatus payment = client.get().uri("/{id}", paymentId)
                    .header("Authorization", authorization)
                    .retrieve().body(PaymentStatus.class);
            return payment != null && "AUTHORIZED".equals(payment.status())
                    && BigDecimal.valueOf(total).compareTo(payment.amount()) == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
