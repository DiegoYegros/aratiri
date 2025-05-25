package com.aratiri.aratiri.repository;

import com.aratiri.aratiri.entity.LightningInvoiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LightningInvoiceRepository extends JpaRepository<LightningInvoiceEntity, String> {
    Optional<LightningInvoiceEntity> findByPaymentRequest(String paymentRequest);
}
