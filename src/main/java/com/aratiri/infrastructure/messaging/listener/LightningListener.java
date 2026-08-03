package com.aratiri.infrastructure.messaging.listener;

import com.aratiri.infrastructure.persistence.jpa.entity.InvoiceSubscriptionState;
import com.aratiri.infrastructure.persistence.jpa.repository.InvoiceSubscriptionStateRepository;
import com.aratiri.payments.application.invoice.InvoiceProcessorService;
import com.aratiri.payments.domain.LightningInvoiceUpdate;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lnrpc.Invoice;
import lnrpc.InvoiceSubscription;
import lnrpc.LightningGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class LightningListener {

    private static final Logger logger = LoggerFactory.getLogger(LightningListener.class);
    private final LightningGrpc.LightningStub lightningAsyncStub;
    private final AtomicBoolean isListening = new AtomicBoolean(false);
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(false);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private final AtomicLong streamEpoch = new AtomicLong(0);
    private final InvoiceProcessorService invoiceProcessorService;
    private final InvoiceSubscriptionStateRepository invoiceSubscriptionStateRepository;
    private final AtomicReference<ClientCallStreamObserver<InvoiceSubscription>> invoiceRequestStream =
            new AtomicReference<>();
    private final AtomicLong activeStreamEpoch = new AtomicLong(-1L);

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
        cancelActiveStream("shutdown");
        shutdownLatch.countDown();
    }

    @EventListener
    public void handleContextClosedEvent(ContextClosedEvent event) {
        logger.info("Application context closing, stopping invoice listener");
        stopListening();
    }

    public void subscribeToInvoices() {
        if (isListening.get()) {
            logger.debug("Already listening to invoices, skipping");
            return;
        }

        // Ensure any prior stream is cancelled before opening a new one (no overlapping streams).
        cancelActiveStream("replacing stream before subscribe");

        try {
            establishSubscription();
        } catch (Exception e) {
            logger.error("Failed to establish invoice subscription", e);
            isListening.set(false);
            cancelActiveStream("subscribe failure");
            requestReconnectIfRunning();
        }
    }

    private void establishSubscription() {
        logger.info("Establishing invoice subscription stream");
        long epoch = streamEpoch.incrementAndGet();
        activeStreamEpoch.set(epoch);
        isListening.set(true);
        InvoiceSubscriptionState state = invoiceSubscriptionStateRepository.findById("singleton")
                .orElse(InvoiceSubscriptionState.builder().id("singleton").build());
        logger.info("Subscribing with addIndex [{}] and settleIndex [{}]", state.getAddIndex(), state.getSettleIndex());
        InvoiceSubscription subscriptionRequest = InvoiceSubscription.newBuilder()
                .setAddIndex(state.getAddIndex())
                .setSettleIndex(state.getSettleIndex())
                .build();

        lightningAsyncStub.subscribeInvoices(subscriptionRequest, newInvoiceStreamObserver(epoch));
        logger.info("Successfully subscribed to invoice updates");
    }

    private ClientResponseObserver<InvoiceSubscription, Invoice> newInvoiceStreamObserver(long epoch) {
        return new ClientResponseObserver<>() {
            @Override
            public void beforeStart(ClientCallStreamObserver<InvoiceSubscription> requestStream) {
                invoiceRequestStream.set(requestStream);
            }

            @Override
            public void onNext(Invoice invoice) {
                handleInvoiceOnNext(invoice, epoch);
            }

            @Override
            public void onError(Throwable throwable) {
                logger.error("Error in invoice subscription stream, message: {}", throwable.getMessage(), throwable);
                failActiveStream(epoch, "stream error");
            }

            @Override
            public void onCompleted() {
                logger.info("Invoice subscription stream completed");
                failActiveStream(epoch, "stream completed");
            }
        };
    }

    private void handleInvoiceOnNext(Invoice invoice, long epoch) {
        if (activeStreamEpoch.get() != epoch || !isListening.get()) {
            logger.warn("Invoice received on inactive/cancelled stream epoch={}, ignoring", epoch);
            return;
        }
        try {
            // Synchronous ordered processing: failure must stop the stream before N+1.
            invoiceProcessorService.processInvoiceUpdate(toDomain(invoice));
        } catch (Exception e) {
            logger.error("Invoice update processing failed; cancelling stream to reconnect from last committed cursor. paymentRequest={}",
                    invoice != null ? invoice.getPaymentRequest() : "unknown", e);
            failActiveStream(epoch, "processing failure");
        }
    }

    private void failActiveStream(long epoch, String reason) {
        if (activeStreamEpoch.get() != epoch) {
            return;
        }
        isListening.set(false);
        requestReconnectIfRunning();
        cancelActiveStream(reason);
    }

    private void requestReconnectIfRunning() {
        if (shutdownLatch.getCount() > 0) {
            shouldReconnect.set(true);
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

    /**
     * Cancels the server stream via the client call observer. Calling the response
     * observer's {@code onCompleted()} only toggles local callbacks and does not cancel LND.
     */
    void cancelActiveStream(String reason) {
        ClientCallStreamObserver<InvoiceSubscription> stream = invoiceRequestStream.getAndSet(null);
        activeStreamEpoch.set(-1L);
        if (stream == null) {
            return;
        }
        try {
            stream.cancel(reason, null);
        } catch (Exception e) {
            logger.debug("Error cancelling invoice stream ({}): {}", reason, e.getMessage());
        }
    }

    private LightningInvoiceUpdate toDomain(Invoice invoice) {
        return new LightningInvoiceUpdate(
                invoice.getPaymentRequest(),
                HexFormat.of().formatHex(invoice.getRHash().toByteArray()),
                toDomainState(invoice.getState()),
                invoice.getAmtPaidSat(),
                invoice.getAddIndex(),
                invoice.getSettleIndex()
        );
    }

    private LightningInvoiceUpdate.State toDomainState(Invoice.InvoiceState state) {
        return switch (state) {
            case OPEN -> LightningInvoiceUpdate.State.OPEN;
            case SETTLED -> LightningInvoiceUpdate.State.SETTLED;
            case CANCELED -> LightningInvoiceUpdate.State.CANCELED;
            case ACCEPTED -> LightningInvoiceUpdate.State.ACCEPTED;
            case UNRECOGNIZED -> LightningInvoiceUpdate.State.UNKNOWN;
        };
    }
}
