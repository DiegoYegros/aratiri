package com.aratiri.payments.application.invoice;

import com.aratiri.infrastructure.messaging.EventPublisher;
import com.aratiri.infrastructure.messaging.KafkaTopicNames;
import com.aratiri.infrastructure.persistence.jpa.entity.InvoiceSubscriptionState;
import com.aratiri.infrastructure.persistence.jpa.repository.InvoiceSubscriptionStateRepository;
import com.aratiri.invoices.application.InvoiceSettledPublication;
import com.aratiri.invoices.application.InvoiceStateUpdate;
import com.aratiri.invoices.application.InvoiceStateUpdateResult;
import com.aratiri.invoices.application.port.in.InvoiceSettlementPort;
import com.aratiri.payments.domain.LightningInvoiceUpdate;
import com.aratiri.errors.ApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

/**
 * Strictly ordered invoice subscription processor. Must be invoked synchronously on the
 * listener thread; never {@code @Async}. Cursor advances only after a successful commit.
 */
@Service
public class InvoiceProcessorService {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceProcessorService.class);

    private final InvoiceSettlementPort invoiceSettlementPort;
    private final EventPublisher eventPublisher;
    private final InvoiceSubscriptionStateRepository invoiceSubscriptionStateRepository;

    public InvoiceProcessorService(InvoiceSettlementPort invoiceSettlementPort,
                                   EventPublisher eventPublisher,
                                   InvoiceSubscriptionStateRepository invoiceSubscriptionStateRepository) {
        this.invoiceSettlementPort = invoiceSettlementPort;
        this.eventPublisher = eventPublisher;
        this.invoiceSubscriptionStateRepository = invoiceSubscriptionStateRepository;
    }

    @Transactional
    public void processInvoiceUpdate(LightningInvoiceUpdate invoice) {
        InvoiceStateUpdateResult result = invoiceSettlementPort.recordInvoiceStateUpdate(new InvoiceStateUpdate(
                invoice.paymentRequest(),
                invoice.paymentHash(),
                mapInvoiceState(invoice.state()),
                invoice.amountPaidSat()
        ));

        if (result.stateChanged()) {
            result.settledPublication().ifPresent(publication -> {
                saveInvoiceSettledEvent(publication);
                logger.info("Published INVOICE_SETTLED event to Kafka for invoiceId: {}", publication.invoiceId());
            });
        }

        InvoiceSubscriptionState state = invoiceSubscriptionStateRepository.findById("singleton")
                .orElseGet(() -> InvoiceSubscriptionState.builder().id("singleton").addIndex(0).settleIndex(0).build());
        if (invoice.addIndex() > state.getAddIndex()) {
            state.setAddIndex(invoice.addIndex());
        }
        if (invoice.settleIndex() > state.getSettleIndex()) {
            state.setSettleIndex(invoice.settleIndex());
        }
        invoiceSubscriptionStateRepository.save(state);
    }

    private void saveInvoiceSettledEvent(InvoiceSettledPublication publication) {
        try {
            eventPublisher.publish(KafkaTopicNames.INVOICE_SETTLED, publication.invoiceId(), publication.event());
        } catch (Exception e) {
            throw new ApplicationException("Failed to publish event for settled invoice.", HttpStatus.INTERNAL_SERVER_ERROR.value(), e);
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
