package com.aratiri.repository;

import com.aratiri.entity.LightningInvoiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LightningInvoiceRepository extends JpaRepository<LightningInvoiceEntity, String> {
    Optional<LightningInvoiceEntity> findByPaymentRequest(String paymentRequest);

    Optional<LightningInvoiceEntity> findByPaymentHash(String paymentHash);

    Optional<LightningInvoiceEntity> findByPaymentHashAndInvoiceState(String paymentHash, LightningInvoiceEntity.InvoiceState invoiceState);
}
