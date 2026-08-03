package com.aratiri.invoices.application;

import com.aratiri.invoices.application.event.InvoiceSettledEvent;
import com.aratiri.invoices.application.port.in.InvoiceSettlementPort;
import com.aratiri.invoices.application.port.out.LightningInvoicePersistencePort;
import com.aratiri.invoices.application.port.out.LinkedPaymentRequestPort;
import com.aratiri.invoices.application.port.out.OwnedPaymentRequestInvoiceSeed;
import com.aratiri.invoices.domain.LightningInvoice;
import com.aratiri.errors.ApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class InvoiceSettlementService implements InvoiceSettlementPort {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final LightningInvoicePersistencePort lightningInvoicePersistencePort;
    private final LinkedPaymentRequestPort linkedPaymentRequestPort;
    private final Clock clock;

    public InvoiceSettlementService(
            LightningInvoicePersistencePort lightningInvoicePersistencePort,
            LinkedPaymentRequestPort linkedPaymentRequestPort,
            Clock clock
    ) {
        this.lightningInvoicePersistencePort = lightningInvoicePersistencePort;
        this.linkedPaymentRequestPort = linkedPaymentRequestPort;
        this.clock = clock;
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
                .orElseThrow(() -> new ApplicationException("Internal invoice not found for payment hash."));

        if (!command.receiverId().equals(invoice.userId())) {
            throw new ApplicationException("Internal invoice does not correspond to transfer receiver.");
        }
        if (invoice.invoiceState() == LightningInvoice.InvoiceState.SETTLED) {
            if (invoice.amountPaidSats() != command.amountSat()) {
                throw new ApplicationException("Internal invoice settlement amount does not match transfer amount.");
            }
            return InternalInvoiceSettlementFacts.from(invoice);
        }

        LightningInvoice settledInvoice = lightningInvoicePersistencePort.save(invoice.settle(command.amountSat(), LocalDateTime.now(clock)));
        linkedPaymentRequestPort.markPaidByPaymentHash(settledInvoice.paymentHash(), clock.instant());
        return InternalInvoiceSettlementFacts.from(settledInvoice);
    }

    @Override
    @Transactional
    public InvoiceStateUpdateResult recordInvoiceStateUpdate(InvoiceStateUpdate update) {
        Optional<LightningInvoice> optionalInvoice = findInvoice(update);
        if (optionalInvoice.isEmpty()) {
            optionalInvoice = recoverOwnedInvoice(update);
        }
        if (optionalInvoice.isEmpty()) {
            if (update.state() == InvoiceStateUpdate.State.SETTLED && update.paymentHash() != null) {
                Optional<OwnedPaymentRequestInvoiceSeed> owned =
                        linkedPaymentRequestPort.findOwnedInvoiceSeedByPaymentHash(update.paymentHash());
                if (owned.isPresent()) {
                    throw new ApplicationException(
                            "Owned payment-request invoice is settled on LND but could not be recovered locally for hash "
                                    + update.paymentHash()
                    );
                }
            }
            logger.debug(
                    "Invoice not found in database, skipping: paymentRequest={} paymentHash={}",
                    update.paymentRequest(),
                    update.paymentHash()
            );
            return InvoiceStateUpdateResult.ignored();
        }

        LightningInvoice invoice = optionalInvoice.get();
        LightningInvoice.InvoiceState newState = mapInvoiceState(update.state());
        if (invoice.invoiceState().equals(newState)) {
            return InvoiceStateUpdateResult.ignored();
        }

        logger.info("Invoice state changed from {} to {} for payment hash: {}",
                invoice.invoiceState(), newState, invoice.paymentHash());

        if (newState == LightningInvoice.InvoiceState.SETTLED) {
            LightningInvoice settledInvoice = lightningInvoicePersistencePort.save(
                    invoice.settle(update.amountPaidSat(), LocalDateTime.now(clock))
            );
            linkedPaymentRequestPort.markPaidByPaymentHash(settledInvoice.paymentHash(), clock.instant());
            logger.info("Invoice settled for {} sats: {}", update.amountPaidSat(), settledInvoice.paymentHash());
            InvoiceSettledEvent eventPayload = new InvoiceSettledEvent(
                    settledInvoice.userId(),
                    settledInvoice.amountSats(),
                    settledInvoice.paymentHash(),
                    LocalDateTime.now(clock),
                    settledInvoice.memo()
            );
            return InvoiceStateUpdateResult.settled(new InvoiceSettledPublication(settledInvoice.id(), eventPayload));
        }

        lightningInvoicePersistencePort.save(invoice.withState(newState));
        return InvoiceStateUpdateResult.changed();
    }

    private Optional<LightningInvoice> findInvoice(InvoiceStateUpdate update) {
        if (update.paymentHash() != null && !update.paymentHash().isBlank()) {
            Optional<LightningInvoice> byHash = lightningInvoicePersistencePort.findByPaymentHash(update.paymentHash());
            if (byHash.isPresent()) {
                return byHash;
            }
        }
        if (update.paymentRequest() != null && !update.paymentRequest().isBlank()) {
            return lightningInvoicePersistencePort.findByPaymentRequest(update.paymentRequest());
        }
        return Optional.empty();
    }

    private Optional<LightningInvoice> recoverOwnedInvoice(InvoiceStateUpdate update) {
        if (update.paymentHash() == null || update.paymentHash().isBlank()) {
            return Optional.empty();
        }
        Optional<OwnedPaymentRequestInvoiceSeed> seed =
                linkedPaymentRequestPort.findOwnedInvoiceSeedByPaymentHash(update.paymentHash());
        if (seed.isEmpty()) {
            return Optional.empty();
        }
        OwnedPaymentRequestInvoiceSeed owned = seed.get();
        String bolt11 = update.paymentRequest() != null && !update.paymentRequest().isBlank()
                ? update.paymentRequest()
                : owned.paymentRequest();
        if (bolt11 == null || bolt11.isBlank()) {
            logger.error(
                    "Cannot recover owned invoice without BOLT11 for paymentHash={}",
                    update.paymentHash()
            );
            return Optional.empty();
        }
        LightningInvoice created = new LightningInvoice(
                UUID.randomUUID().toString(),
                owned.userId(),
                owned.paymentHash(),
                owned.preimage(),
                bolt11,
                LightningInvoice.InvoiceState.OPEN,
                owned.amountSats(),
                LocalDateTime.now(clock),
                owned.expirySeconds(),
                0,
                null,
                owned.memo(),
                null,
                null
        );
        LightningInvoice saved = lightningInvoicePersistencePort.save(created);
        logger.warn(
                "Recovered missing local lightning invoice for owned payment request hash={}",
                owned.paymentHash()
        );
        return Optional.of(saved);
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
