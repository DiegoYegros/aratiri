package com.aratiri.payments.infrastructure.persistence;

import com.aratiri.infrastructure.persistence.jpa.entity.LightningInvoiceEntity;
import com.aratiri.payments.application.port.out.LightningInvoicePort;
import com.aratiri.payments.domain.InternalLightningInvoice;
import com.aratiri.infrastructure.persistence.jpa.repository.LightningInvoiceRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class LightningInvoiceRepositoryAdapter implements LightningInvoicePort {

    private final LightningInvoiceRepository lightningInvoiceRepository;

    public LightningInvoiceRepositoryAdapter(LightningInvoiceRepository lightningInvoiceRepository) {
        this.lightningInvoiceRepository = lightningInvoiceRepository;
    }

    @Override
    public Optional<InternalLightningInvoice> findByPaymentHash(String paymentHash) {
        return lightningInvoiceRepository.findByPaymentHash(paymentHash)
                .map(this::mapToDomain);
    }

    private InternalLightningInvoice mapToDomain(LightningInvoiceEntity entity) {
        var state = entity.getInvoiceState() == LightningInvoiceEntity.InvoiceState.SETTLED
                ? InternalLightningInvoice.InvoiceState.SETTLED
                : InternalLightningInvoice.InvoiceState.PENDING;
        return new InternalLightningInvoice(entity.getUserId(), state);
    }
}
