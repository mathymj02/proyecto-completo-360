package com.pedidos360.payments.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

// Alias de prueba: nunca aceptar ni almacenar el numero real de tarjeta.
public record StartPaymentRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]{2,31}$") String cardAlias
) {}
