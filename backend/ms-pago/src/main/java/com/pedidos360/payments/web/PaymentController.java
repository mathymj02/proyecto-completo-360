package com.pedidos360.payments.web;

import com.pedidos360.payments.model.StartPaymentRequest;
import com.pedidos360.payments.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService payments;

    public record CommitRequest(@NotBlank String token) {}

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    // Webpay vuelve por GET normalmente; en integración una cancelación puede usar POST.
    // El fragmento no se envía al servidor frontend y evita registrar el token en Nginx.
    @RequestMapping(value = "/return/{paymentId}", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Void> webpayReturn(@PathVariable String paymentId,
                                               @RequestParam Map<String, String> params) {
        String token = params.get("token_ws");
        String destination = "/payment-return/" + paymentId;
        if (token != null && !token.isBlank()) {
            destination += "#token_ws=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, destination)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .build();
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
