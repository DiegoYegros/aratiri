package com.aratiri.payments.application.invoice;

import com.aratiri.infrastructure.messaging.outbox.OutboxWriter;
import com.aratiri.infrastructure.persistence.jpa.entity.InvoiceSubscriptionState;
import com.aratiri.infrastructure.persistence.jpa.entity.LightningInvoiceEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.InvoiceSubscriptionStateRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.LightningInvoiceRepository;
import com.aratiri.invoices.application.event.InvoiceSettledEvent;
import com.aratiri.payments.domain.LightningInvoiceUpdate;
import com.aratiri.shared.exception.AratiriException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class InvoiceProcessorService {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceProcessorService.class);

    private final LightningInvoiceRepository lightningInvoiceRepository;
    private final OutboxWriter outboxWriter;
    private final InvoiceSubscriptionStateRepository invoiceSubscriptionStateRepository;

    public InvoiceProcessorService(LightningInvoiceRepository lightningInvoiceRepository,
                                   OutboxWriter outboxWriter,
                                   InvoiceSubscriptionStateRepository invoiceSubscriptionStateRepository) {
        this.lightningInvoiceRepository = lightningInvoiceRepository;
        this.outboxWriter = outboxWriter;
        this.invoiceSubscriptionStateRepository = invoiceSubscriptionStateRepository;
    }

    @Transactional
    @Async
    public void processInvoiceUpdate(LightningInvoiceUpdate invoice) {
        if (invoice.state() == LightningInvoiceUpdate.State.OPEN) {
            return;
        }

        Optional<LightningInvoiceEntity> optionalInvoiceEntity =
                lightningInvoiceRepository.findByPaymentRequest(invoice.paymentRequest());

        if (optionalInvoiceEntity.isEmpty()) {
            logger.debug("Invoice not found in database, skipping: {}", invoice.paymentRequest());
            return;
        }

        LightningInvoiceEntity invoiceEntity = optionalInvoiceEntity.get();
        LightningInvoiceEntity.InvoiceState newState = mapInvoiceState(invoice.state());

        if (invoiceEntity.getInvoiceState().equals(newState)) {
            return;
        }

        logger.info("Invoice state changed from {} to {} for payment request: {}",
                invoiceEntity.getInvoiceState(), newState, invoice.paymentRequest());

        invoiceEntity.setInvoiceState(newState);
        if (newState == LightningInvoiceEntity.InvoiceState.SETTLED) {
            invoiceEntity.setSettledAt(LocalDateTime.now());
            invoiceEntity.setAmountPaidSats(invoice.amountPaidSat());
            logger.info("Invoice settled for {} sats: {}", invoice.amountPaidSat(), invoice.paymentRequest());

            lightningInvoiceRepository.save(invoiceEntity);

            InvoiceSettledEvent eventPayload = new InvoiceSettledEvent(
                    invoiceEntity.getUserId(),
                    invoiceEntity.getAmountSats(),
                    invoiceEntity.getPaymentHash(),
                    LocalDateTime.now(),
                    invoiceEntity.getMemo()
            );

            saveInvoiceSettledEvent(invoiceEntity, eventPayload);
            logger.info("Saved INVOICE_SETTLED event to outbox for invoiceId: {}", invoiceEntity.getId());
        } else {
            lightningInvoiceRepository.save(invoiceEntity);
        }
        InvoiceSubscriptionState state = invoiceSubscriptionStateRepository.findById("singleton").orElse(InvoiceSubscriptionState.builder().id("singleton").build());
        state.setAddIndex(invoice.addIndex());
        state.setSettleIndex(invoice.settleIndex());
        invoiceSubscriptionStateRepository.save(state);
    }

    private void saveInvoiceSettledEvent(LightningInvoiceEntity invoiceEntity, InvoiceSettledEvent eventPayload) {
        try {
            outboxWriter.publishInvoiceSettled(invoiceEntity.getId(), eventPayload);
        } catch (Exception e) {
            throw new AratiriException("Failed to create outbox event for settled invoice.", HttpStatus.INTERNAL_SERVER_ERROR.value(), e);
        }
    }

    private LightningInvoiceEntity.InvoiceState mapInvoiceState(LightningInvoiceUpdate.State invoiceState) {
        return switch (invoiceState) {
            case OPEN -> LightningInvoiceEntity.InvoiceState.OPEN;
            case SETTLED -> LightningInvoiceEntity.InvoiceState.SETTLED;
            case CANCELED -> LightningInvoiceEntity.InvoiceState.CANCELED;
            case ACCEPTED -> LightningInvoiceEntity.InvoiceState.ACCEPTED;
            default -> {
                logger.warn("Unknown invoice state: {}, defaulting to OPEN", invoiceState);
                yield LightningInvoiceEntity.InvoiceState.OPEN;
            }
        };
    }
}
