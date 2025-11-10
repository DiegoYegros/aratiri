package com.aratiri.invoices.infrastructure.persistence;

import com.aratiri.infrastructure.persistence.jpa.entity.LightningInvoiceEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.LightningInvoiceRepository;
import com.aratiri.invoices.application.port.out.LightningInvoicePersistencePort;
import com.aratiri.invoices.domain.LightningInvoice;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("invoicesLightningInvoiceRepositoryAdapter")
public class LightningInvoiceRepositoryAdapter implements LightningInvoicePersistencePort {

    private final LightningInvoiceRepository lightningInvoiceRepository;

    public LightningInvoiceRepositoryAdapter(LightningInvoiceRepository lightningInvoiceRepository) {
        this.lightningInvoiceRepository = lightningInvoiceRepository;
    }

    @Override
    public LightningInvoice save(LightningInvoice invoice) {
        LightningInvoiceEntity entity = mapToEntity(invoice);
        LightningInvoiceEntity saved = lightningInvoiceRepository.save(entity);
        return mapToDomain(saved);
    }

    @Override
    public Optional<LightningInvoice> findSettledByPaymentHash(String paymentHash) {
        return lightningInvoiceRepository
                .findByPaymentHashAndInvoiceState(paymentHash, LightningInvoiceEntity.InvoiceState.SETTLED)
                .map(this::mapToDomain);
    }

    private LightningInvoiceEntity mapToEntity(LightningInvoice invoice) {
        return LightningInvoiceEntity.builder()
                .id(invoice.id())
                .userId(invoice.userId())
                .paymentHash(invoice.paymentHash())
                .preimage(invoice.preimage())
                .paymentRequest(invoice.paymentRequest())
                .invoiceState(mapState(invoice.invoiceState()))
                .amountSats(invoice.amountSats())
                .createdAt(invoice.createdAt())
                .expiry(invoice.expiry())
                .amountPaidSats(invoice.amountPaidSats())
                .settledAt(invoice.settledAt())
                .memo(invoice.memo())
                .build();
    }

    private LightningInvoice mapToDomain(LightningInvoiceEntity entity) {
        return new LightningInvoice(
                entity.getId(),
                entity.getUserId(),
                entity.getPaymentHash(),
                entity.getPreimage(),
                entity.getPaymentRequest(),
                mapState(entity.getInvoiceState()),
                entity.getAmountSats(),
                entity.getCreatedAt(),
                entity.getExpiry(),
                entity.getAmountPaidSats(),
                entity.getSettledAt(),
                entity.getMemo()
        );
    }

    private LightningInvoiceEntity.InvoiceState mapState(LightningInvoice.InvoiceState state) {
        return LightningInvoiceEntity.InvoiceState.valueOf(state.name());
    }

    private LightningInvoice.InvoiceState mapState(LightningInvoiceEntity.InvoiceState state) {
        return LightningInvoice.InvoiceState.valueOf(state.name());
    }
}
