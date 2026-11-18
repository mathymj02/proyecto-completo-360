package com.pedidos360.payments.service;

import com.pedidos360.payments.model.Payment;
import com.pedidos360.payments.model.PaymentRepository;
import com.pedidos360.payments.model.StartPaymentRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentRepository repository;
    private final WebpayClient webpay;
    private final PaymentEvents events;
    private final String salt;
    private final String frontendBaseUrl;

    public record StartResult(String paymentId, String status, String url, String token) {}
    public record StatusResult(String paymentId, String status, BigDecimal amount) {}

    public PaymentService(PaymentRepository repository, WebpayClient webpay, PaymentEvents events,
                          @Value("${payments.card-hash-salt}") String salt,
                          @Value("${payments.frontend-base-url}") String frontendBaseUrl) {
        this.repository = repository;
        this.webpay = webpay;
        this.events = events;
        this.salt = salt;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    // La politica de 10 s usa un alias de prueba. Webpay Plus captura la tarjeta
    // en su propia pagina y no entrega al comercio un identificador estable.
    public StartResult start(String userId, StartPaymentRequest request) {
        String fingerprint = fingerprint(request.cardAlias());
        String paymentId = "PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Payment payment = new Payment(paymentId, userId, request.amount(), fingerprint, Instant.now());
        boolean blocked;
        synchronized (this) {
            blocked = repository.countByCardFingerprintAndCreatedAtAfter(
                    fingerprint, Instant.now().minusSeconds(10)) >= 3;
            if (blocked) payment.setStatus("BLOCKED");
            repository.save(payment);
        }
        if (blocked) {
            events.publish(userId, paymentId, "BLOCKED", "Cuarto intento con el mismo alias en 10 segundos");
            return new StartResult(paymentId, "BLOCKED", null, null);
        }
        try {
            String returnUrl = frontendBaseUrl + "/payment-return/" + paymentId;
            WebpayClient.StartResult webpayResult = webpay.create(paymentId, userId,
                    request.amount(), returnUrl);
            payment.setWebpayToken(webpayResult.token());
            payment.setStatus("INITIATED");
            repository.save(payment);
            events.publish(userId, paymentId, "INITIATED", "Webpay en ambiente de integracion");
            return new StartResult(paymentId, "INITIATED",
                    webpayResult.url(), webpayResult.token());
        } catch (Exception e) {
            payment.setStatus("ERROR");
            repository.save(payment);
            events.publish(userId, paymentId, "ERROR", "No se pudo iniciar Webpay");
            throw new IllegalStateException("No se pudo iniciar la transaccion de prueba en Webpay", e);
        }
    }

    public synchronized StatusResult commit(String paymentId, String userId, String token) {
        Payment payment = owned(paymentId, userId);
        if ("AUTHORIZED".equals(payment.getStatus())) return status(payment);
        if (!"INITIATED".equals(payment.getStatus()) || token == null
                || !token.equals(payment.getWebpayToken())) {
            throw new IllegalArgumentException("Token o estado de pago invalido");
        }
        WebpayClient.CommitResult result = webpay.commit(token);
        boolean approved = paymentId.equals(result.buyOrder())
                && payment.getAmount().compareTo(result.amount()) == 0
                && "AUTHORIZED".equals(result.status()) && result.responseCode() == 0;
        payment.setStatus(approved ? "AUTHORIZED" : "DECLINED");
        repository.save(payment);
        events.publish(userId, paymentId, payment.getStatus(),
                approved ? "Pago confirmado" : "Pago no autorizado o datos inconsistentes");
        return status(payment);
    }

    public StatusResult get(String paymentId, String userId) {
        return status(owned(paymentId, userId));
    }

    private Payment owned(String paymentId, String userId) {
        Payment payment = repository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Pago inexistente"));
        if (!payment.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Pago inexistente");
        }
        return payment;
    }

    private static StatusResult status(Payment payment) {
        return new StatusResult(payment.getId(), payment.getStatus(), payment.getAmount());
    }

    private String fingerprint(String alias) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((salt + ":" + alias).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular huella de alias", e);
        }
    }
}
