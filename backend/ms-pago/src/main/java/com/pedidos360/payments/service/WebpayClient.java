package com.pedidos360.payments.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class WebpayClient {
    private final RestClient client;
    private static final String PATH = "/rswebpaytransaction/api/webpay/v1.2/transactions";

    public WebpayClient(@Value("${payments.webpay.base-url}") String baseUrl,
                        @Value("${payments.webpay.commerce-code}") String commerceCode,
                        @Value("${payments.webpay.api-key}") String apiKey) {
        this.client = RestClient.builder().baseUrl(baseUrl)
                .defaultHeader("Tbk-Api-Key-Id", commerceCode)
                .defaultHeader("Tbk-Api-Key-Secret", apiKey)
                .build();
    }

    public record StartResult(String token, String url) {}
    public record CommitResult(String buyOrder, BigDecimal amount, String status, int responseCode) {}

    public StartResult create(String paymentId, String userId, BigDecimal amount, String returnUrl) {
        JsonNode response = client.post().uri(PATH).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("buy_order", paymentId, "session_id", userId,
                        "amount", amount, "return_url", returnUrl))
                .retrieve().body(JsonNode.class);
        if (response == null || response.path("token").asText().isBlank()
                || response.path("url").asText().isBlank()) {
            throw new IllegalStateException("Webpay no devolvio token y URL");
        }
        return new StartResult(response.path("token").asText(), response.path("url").asText());
    }

    public CommitResult commit(String token) {
        JsonNode response = client.put().uri(PATH + "/{token}", token)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of())
                .retrieve().body(JsonNode.class);
        if (response == null) throw new IllegalStateException("Webpay no devolvio resultado");
        return new CommitResult(response.path("buy_order").asText(),
                response.path("amount").decimalValue(), response.path("status").asText(),
                response.path("response_code").asInt(-1));
    }
}
