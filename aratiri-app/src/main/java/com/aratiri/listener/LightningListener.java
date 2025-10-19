package com.aratiri.listener;

import com.aratiri.entity.InvoiceSubscriptionState;
import com.aratiri.repository.InvoiceSubscriptionStateRepository;
import com.aratiri.payments.application.invoice.InvoiceProcessorService;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class LightningListener {

    private static final Logger logger = LoggerFactory.getLogger(LightningListener.class);
    private final LightningGrpc.LightningStub lightningAsyncStub;
    private final AtomicBoolean isListening = new AtomicBoolean(false);
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(false);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private final InvoiceProcessorService invoiceProcessorService;
    private final InvoiceSubscriptionStateRepository invoiceSubscriptionStateRepository;
    private StreamObserver<Invoice> invoiceStreamObserver;

    public LightningListener(LightningGrpc.LightningStub lightningAsyncStub, InvoiceProcessorService invoiceProcessorService, InvoiceSubscriptionStateRepository invoiceSubscriptionStateRepository) {
        this.lightningAsyncStub = lightningAsyncStub;
        this.invoiceProcessorService = invoiceProcessorService;
        this.invoiceSubscriptionStateRepository = invoiceSubscriptionStateRepository;
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
            InvoiceSubscriptionState state = invoiceSubscriptionStateRepository.findById("singleton").orElse(InvoiceSubscriptionState.builder().id("singleton").build());
            logger.info("Subscribing with addIndex [{}] and settleIndex [{}]", state.getAddIndex(), state.getSettleIndex());
            InvoiceSubscription subscriptionRequest = InvoiceSubscription.newBuilder()
                    .setAddIndex(state.getAddIndex())
                    .setSettleIndex(state.getSettleIndex())
                    .build();

            invoiceStreamObserver = new StreamObserver<>() {
                @Override
                public void onNext(Invoice invoice) {
                    if (!isListening.get()) {
                        logger.warn("Invoice received during shutdown, ignoring: {}", invoice.getPaymentRequest());
                        return;
                    }
                    try {
                        invoiceProcessorService.processInvoiceUpdate(invoice);
                    } catch (Exception e) {
                        logger.error("Unhandled exception in handleInvoiceUpdate for payment request: {}",
                                invoice != null ? invoice.getPaymentRequest() : "unknown", e);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    logger.error("Error in invoice subscription stream, message: {}", throwable.getMessage(), throwable);
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
}