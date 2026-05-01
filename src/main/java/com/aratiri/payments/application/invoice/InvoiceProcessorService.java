package com.aratiri.payments.application.invoice;

import com.aratiri.infrastructure.messaging.outbox.OutboxWriter;
import com.aratiri.infrastructure.persistence.jpa.entity.InvoiceSubscriptionState;
import com.aratiri.infrastructure.persistence.jpa.repository.InvoiceSubscriptionStateRepository;
import com.aratiri.invoices.application.InvoiceSettledPublication;
import com.aratiri.invoices.application.InvoiceStateUpdate;
import com.aratiri.invoices.application.InvoiceStateUpdateResult;
import com.aratiri.invoices.application.port.in.InvoiceSettlementPort;
import com.aratiri.payments.domain.LightningInvoiceUpdate;
import com.aratiri.shared.exception.AratiriException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

@Service
public class InvoiceProcessorService {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceProcessorService.class);

    private final InvoiceSettlementPort invoiceSettlementPort;
    private final OutboxWriter outboxWriter;
    private final InvoiceSubscriptionStateRepository invoiceSubscriptionStateRepository;

    public InvoiceProcessorService(InvoiceSettlementPort invoiceSettlementPort,
                                   OutboxWriter outboxWriter,
                                   InvoiceSubscriptionStateRepository invoiceSubscriptionStateRepository) {
        this.invoiceSettlementPort = invoiceSettlementPort;
        this.outboxWriter = outboxWriter;
        this.invoiceSubscriptionStateRepository = invoiceSubscriptionStateRepository;
    }

    @Transactional
    @Async
    public void processInvoiceUpdate(LightningInvoiceUpdate invoice) {
        if (invoice.state() == LightningInvoiceUpdate.State.OPEN) {
            return;
        }

        InvoiceStateUpdateResult result = invoiceSettlementPort.recordInvoiceStateUpdate(new InvoiceStateUpdate(
                invoice.paymentRequest(),
                mapInvoiceState(invoice.state()),
                invoice.amountPaidSat()
        ));
        if (!result.stateChanged()) {
            return;
        }

        result.settledPublication().ifPresent(publication -> {
            saveInvoiceSettledEvent(publication);
            logger.info("Saved INVOICE_SETTLED event to outbox for invoiceId: {}", publication.invoiceId());
        });
        InvoiceSubscriptionState state = invoiceSubscriptionStateRepository.findById("singleton").orElse(InvoiceSubscriptionState.builder().id("singleton").build());
        state.setAddIndex(invoice.addIndex());
        state.setSettleIndex(invoice.settleIndex());
        invoiceSubscriptionStateRepository.save(state);
    }

    private void saveInvoiceSettledEvent(InvoiceSettledPublication publication) {
        try {
            outboxWriter.publishInvoiceSettled(publication.invoiceId(), publication.event());
        } catch (Exception e) {
            throw new AratiriException("Failed to create outbox event for settled invoice.", HttpStatus.INTERNAL_SERVER_ERROR.value(), e);
        }
    }

    private InvoiceStateUpdate.State mapInvoiceState(LightningInvoiceUpdate.State invoiceState) {
        return switch (invoiceState) {
            case OPEN -> InvoiceStateUpdate.State.OPEN;
            case SETTLED -> InvoiceStateUpdate.State.SETTLED;
            case CANCELED -> InvoiceStateUpdate.State.CANCELED;
            case ACCEPTED -> InvoiceStateUpdate.State.ACCEPTED;
            case UNKNOWN -> InvoiceStateUpdate.State.UNKNOWN;
        };
    }
}
