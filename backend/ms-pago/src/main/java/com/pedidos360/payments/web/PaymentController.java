package com.pedidos360.payments.web;

import com.pedidos360.payments.model.StartPaymentRequest;
import com.pedidos360.payments.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService payments;

    public record CommitRequest(@NotBlank String token) {}

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping
    public ResponseEntity<PaymentService.StartResult> start(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartPaymentRequest request) {
        PaymentService.StartResult result = payments.start(userId(jwt), request);
        return result.status().equals("BLOCKED")
                ? ResponseEntity.status(429).body(result)
                : ResponseEntity.status(201).body(result);
    }

    @PostMapping("/{paymentId}/commit")
    public PaymentService.StatusResult commit(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String paymentId, @Valid @RequestBody CommitRequest request) {
        return payments.commit(paymentId, userId(jwt), request.token());
    }

    @GetMapping("/{paymentId}")
    public PaymentService.StatusResult get(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String paymentId) {
        return payments.get(paymentId, userId(jwt));
    }

    private static String userId(Jwt jwt) {
        String oid = jwt.getClaimAsString("oid");
        return oid != null ? oid : jwt.getSubject();
    }
}
