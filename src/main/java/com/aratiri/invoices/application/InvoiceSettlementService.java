package com.aratiri.invoices.application;

import com.aratiri.invoices.application.event.InvoiceSettledEvent;
import com.aratiri.invoices.application.port.in.InvoiceSettlementPort;
import com.aratiri.invoices.application.port.out.LightningInvoicePersistencePort;
import com.aratiri.invoices.domain.LightningInvoice;
import com.aratiri.shared.exception.AratiriException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class InvoiceSettlementService implements InvoiceSettlementPort {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final LightningInvoicePersistencePort lightningInvoicePersistencePort;

    public InvoiceSettlementService(LightningInvoicePersistencePort lightningInvoicePersistencePort) {
        this.lightningInvoicePersistencePort = lightningInvoicePersistencePort;
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceSettlementFacts settlementFacts(String paymentHash) {
        return lightningInvoicePersistencePort.findByPaymentHash(paymentHash)
                .map(InvoiceSettlementFacts::from)
                .orElseGet(() -> InvoiceSettlementFacts.missing(paymentHash));
    }

    @Override
    @Transactional
    public InternalInvoiceSettlementFacts settleInternalInvoice(SettleInternalInvoiceCommand command) {
        LightningInvoice invoice = lightningInvoicePersistencePort.findByPaymentHash(command.paymentHash())
                .orElseThrow(() -> new AratiriException("Internal invoice not found for payment hash."));

        if (!command.receiverId().equals(invoice.userId())) {
            throw new AratiriException("Internal invoice does not correspond to transfer receiver.");
        }
        if (invoice.invoiceState() == LightningInvoice.InvoiceState.SETTLED) {
            if (invoice.amountPaidSats() != command.amountSat()) {
                throw new AratiriException("Internal invoice settlement amount does not match transfer amount.");
            }
            return InternalInvoiceSettlementFacts.from(invoice);
        }

        LightningInvoice settledInvoice = lightningInvoicePersistencePort.save(invoice.settle(command.amountSat(), LocalDateTime.now()));
        return InternalInvoiceSettlementFacts.from(settledInvoice);
    }

    @Override
    @Transactional
    public InvoiceStateUpdateResult recordInvoiceStateUpdate(InvoiceStateUpdate update) {
        Optional<LightningInvoice> optionalInvoice = lightningInvoicePersistencePort.findByPaymentRequest(update.paymentRequest());
        if (optionalInvoice.isEmpty()) {
            logger.debug("Invoice not found in database, skipping: {}", update.paymentRequest());
            return InvoiceStateUpdateResult.ignored();
        }

        LightningInvoice invoice = optionalInvoice.get();
        LightningInvoice.InvoiceState newState = mapInvoiceState(update.state());
        if (invoice.invoiceState().equals(newState)) {
            return InvoiceStateUpdateResult.ignored();
        }

        logger.info("Invoice state changed from {} to {} for payment request: {}",
                invoice.invoiceState(), newState, update.paymentRequest());

        if (newState == LightningInvoice.InvoiceState.SETTLED) {
            LightningInvoice settledInvoice = lightningInvoicePersistencePort.save(invoice.settle(update.amountPaidSat(), LocalDateTime.now()));
            logger.info("Invoice settled for {} sats: {}", update.amountPaidSat(), update.paymentRequest());
            InvoiceSettledEvent eventPayload = new InvoiceSettledEvent(
                    settledInvoice.userId(),
                    settledInvoice.amountSats(),
                    settledInvoice.paymentHash(),
                    LocalDateTime.now(),
                    settledInvoice.memo()
            );
            return InvoiceStateUpdateResult.settled(new InvoiceSettledPublication(settledInvoice.id(), eventPayload));
        }

        lightningInvoicePersistencePort.save(invoice.withState(newState));
        return InvoiceStateUpdateResult.changed();
    }

    private LightningInvoice.InvoiceState mapInvoiceState(InvoiceStateUpdate.State invoiceState) {
        return switch (invoiceState) {
            case OPEN -> LightningInvoice.InvoiceState.OPEN;
            case SETTLED -> LightningInvoice.InvoiceState.SETTLED;
            case CANCELED -> LightningInvoice.InvoiceState.CANCELED;
            case ACCEPTED -> LightningInvoice.InvoiceState.ACCEPTED;
            default -> {
                logger.warn("Unknown invoice state: {}, defaulting to OPEN", invoiceState);
                yield LightningInvoice.InvoiceState.OPEN;
            }
        };
    }
}
