package com.aratiri.aratiri.listener;

import com.aratiri.aratiri.entity.LightningInvoiceEntity;
import com.aratiri.aratiri.repository.LightningInvoiceRepository;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lnrpc.Invoice;
import lnrpc.InvoiceSubscription;
import lnrpc.LightningGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class InvoiceListener {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceListener.class);

    private final LightningGrpc.LightningStub lightningAsyncStub;
    private final LightningInvoiceRepository lightningInvoiceRepository;

    private StreamObserver<Invoice> invoiceStreamObserver;
    private final AtomicBoolean isListening = new AtomicBoolean(false);
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(false);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public InvoiceListener(LightningGrpc.LightningStub lightningAsyncStub,
                           LightningInvoiceRepository lightningInvoiceRepository) {
        this.lightningAsyncStub = lightningAsyncStub;
        this.lightningInvoiceRepository = lightningInvoiceRepository;
    }

    @PostConstruct
    public void startListening() {
        logger.info("Scheduling Lightning Invoice Listener startup...");
        shouldReconnect.set(true);
    }

    @PreDestroy
    public void stopListening() {
        logger.info("Stopping Lightning Invoice Listener...");
        isListening.set(false);
        shouldReconnect.set(false);

        if (invoiceStreamObserver != null) {
            try {
                invoiceStreamObserver.onCompleted();
            } catch (Exception e) {
                logger.debug("Error completing stream observer during shutdown: {}", e.getMessage());
            }
        }

        shutdownLatch.countDown();
    }

    @EventListener
    public void handleContextClosedEvent(ContextClosedEvent event) {
        logger.info("Application context closing, stopping invoice listener");
        stopListening();
    }

    @Async
    public void subscribeToInvoices() {
        if (isListening.get()) {
            logger.debug("Already listening to invoices, skipping");
            return;
        }

        try {
            logger.info("Establishing invoice subscription stream");
            isListening.set(true);

            InvoiceSubscription subscriptionRequest = InvoiceSubscription.newBuilder()
                    .setAddIndex(0)
                    .setSettleIndex(0)
                    .build();

            invoiceStreamObserver = new StreamObserver<>() {
                @Override
                public void onNext(Invoice invoice) {
                    handleInvoiceUpdate(invoice);
                }

                @Override
                public void onError(Throwable throwable) {
                    logger.error("Error in invoice subscription stream", throwable);
                    isListening.set(false);
                    if (shutdownLatch.getCount() > 0) {
                        shouldReconnect.set(true);
                    }
                }

                @Override
                public void onCompleted() {
                    logger.info("Invoice subscription stream completed");
                    isListening.set(false);
                    if (shutdownLatch.getCount() > 0) {
                        shouldReconnect.set(true);
                    }
                }
            };

            lightningAsyncStub.subscribeInvoices(subscriptionRequest, invoiceStreamObserver);
            logger.info("Successfully subscribed to invoice updates");

        } catch (Exception e) {
            logger.error("Failed to establish invoice subscription", e);
            isListening.set(false);
            if (shutdownLatch.getCount() > 0) {
                shouldReconnect.set(true);
            }
        }
    }

    @Scheduled(fixedDelay = 10000)
    public void checkReconnection() {
        if (shouldReconnect.get() && !isListening.get() && shutdownLatch.getCount() > 0) {
            logger.info("Attempting to reconnect to invoice stream");
            shouldReconnect.set(false);
            subscribeToInvoices();
        }
    }

    @Async
    public void handleInvoiceUpdate(Invoice invoice) {
        try {
            if (!isListening.get()) {
                logger.debug("InvoiceListener is shutting down.");
                return;
            }

            if (invoice.getState().equals(Invoice.InvoiceState.OPEN)) {
                logger.debug("Invoice state is open. Returning.");
                return;
            }

            logger.debug("Received invoice update: {}", invoice.getPaymentRequest());

            Optional<LightningInvoiceEntity> optionalInvoiceEntity =
                    lightningInvoiceRepository.findByPaymentRequest(invoice.getPaymentRequest());

            if (optionalInvoiceEntity.isEmpty()) {
                logger.debug("Invoice not found in database, skipping: {}", invoice.getPaymentRequest());
                return;
            }

            LightningInvoiceEntity invoiceEntity = optionalInvoiceEntity.get();
            boolean shouldUpdate = false;

            LightningInvoiceEntity.InvoiceState newState = mapInvoiceState(invoice.getState());
            if (!invoiceEntity.getInvoiceState().equals(newState)) {
                logger.info("Invoice state changed from {} to {} for payment request: {}",
                        invoiceEntity.getInvoiceState(), newState, invoice.getPaymentRequest());

                invoiceEntity.setInvoiceState(newState);
                shouldUpdate = true;

                if (newState == LightningInvoiceEntity.InvoiceState.SETTLED) {
                    invoiceEntity.setSettledAt(LocalDateTime.now());
                    invoiceEntity.setAmountPaidSats(invoice.getAmtPaidSat());
                    logger.info("Invoice settled for {} sats: {}", invoice.getAmtPaidSat(), invoice.getPaymentRequest());
                }
            }

            if (shouldUpdate) {
                if (!isListening.get()) {
                    logger.debug("InvoiceListener shutting down during update. Skipping.");
                    return;
                }

                lightningInvoiceRepository.save(invoiceEntity);
                logger.debug("Updated invoice in database: {}", invoice.getPaymentRequest());

                // TODO: Notify and more stuff here
                onInvoiceStateChanged(invoiceEntity, newState);
            }

        } catch (Exception e) {
            if (isListening.get()) {
                logger.error("Error handling invoice update for payment request: {}",
                        invoice != null ? invoice.getPaymentRequest() : "unknown", e);
            } else {
                logger.debug("Error during shutdown, ignoring: {}", e.getMessage());
            }
        }
    }

    private LightningInvoiceEntity.InvoiceState mapInvoiceState(Invoice.InvoiceState grpcState) {
        switch (grpcState) {
            case OPEN:
                return LightningInvoiceEntity.InvoiceState.OPEN;
            case SETTLED:
                return LightningInvoiceEntity.InvoiceState.SETTLED;
            case CANCELED:
                return LightningInvoiceEntity.InvoiceState.CANCELED;
            case ACCEPTED:
                return LightningInvoiceEntity.InvoiceState.ACCEPTED;
            default:
                logger.warn("Unknown invoice state: {}, defaulting to OPEN", grpcState);
                return LightningInvoiceEntity.InvoiceState.OPEN;
        }
    }

    @Async
    public void onInvoiceStateChanged(LightningInvoiceEntity invoice,
                                      LightningInvoiceEntity.InvoiceState newState) {
        try {
            switch (newState) {
                case SETTLED:
                    logger.info("Processing settled invoice for user {}: {} sats",
                            invoice.getUserId(), invoice.getAmountSats());
                    // TODO: i should credit the user here
                    break;
                case CANCELED:
                    logger.info("Invoice canceled for user {}: {}",
                            invoice.getUserId(), invoice.getPaymentRequest());
                    break;
                case ACCEPTED:
                    logger.info("Invoice accepted for user {}: {}",
                            invoice.getUserId(), invoice.getPaymentRequest());
                    break;
            }
        } catch (Exception e) {
            logger.error("Error processing invoice state change for user {}: {}",
                    invoice.getUserId(), e.getMessage());
        }
    }
}