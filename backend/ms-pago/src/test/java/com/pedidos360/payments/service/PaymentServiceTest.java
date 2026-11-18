package com.pedidos360.payments.service;

import com.pedidos360.payments.model.Payment;
import com.pedidos360.payments.model.PaymentRepository;
import com.pedidos360.payments.model.StartPaymentRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentServiceTest {
    @Test
    void fourthAttemptWithSameAliasIsBlockedBeforeWebpay() {
        PaymentRepository repository = mock(PaymentRepository.class);
        WebpayClient webpay = mock(WebpayClient.class);
        PaymentEvents events = mock(PaymentEvents.class);
        AtomicInteger count = new AtomicInteger();
        when(repository.countByCardFingerprintAndCreatedAtAfter(anyString(), any(Instant.class)))
                .thenAnswer(ignored -> (long) count.get());
        when(repository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if ("CREATED".equals(payment.getStatus()) || "BLOCKED".equals(payment.getStatus())) {
                count.incrementAndGet();
            }
            return payment;
        });
        when(webpay.create(anyString(), anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(new WebpayClient.StartResult("token-test", "https://test.example/pay"));
        PaymentService service = new PaymentService(repository, webpay, events,
                "salt-de-prueba", "https://frontend.example");
        StartPaymentRequest request = new StartPaymentRequest(BigDecimal.valueOf(1000), "tarjetaDemo");

        assertEquals("INITIATED", service.start("usuario-1", request).status());
        assertEquals("INITIATED", service.start("usuario-1", request).status());
        assertEquals("INITIATED", service.start("usuario-1", request).status());
        assertEquals("BLOCKED", service.start("usuario-1", request).status());
        verify(webpay, times(3)).create(anyString(), anyString(), any(BigDecimal.class), anyString());
        verify(events).publish(eq("usuario-1"), anyString(), eq("BLOCKED"), anyString());
    }
}
