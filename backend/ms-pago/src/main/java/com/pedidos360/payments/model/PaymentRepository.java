package com.pedidos360.payments.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface PaymentRepository extends JpaRepository<Payment, String> {
    long countByCardFingerprintAndCreatedAtAfter(String cardFingerprint, Instant since);
}
