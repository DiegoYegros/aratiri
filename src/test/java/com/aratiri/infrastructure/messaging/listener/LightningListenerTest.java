package com.aratiri.infrastructure.messaging.listener;

import com.aratiri.infrastructure.persistence.jpa.entity.InvoiceSubscriptionState;
import com.aratiri.infrastructure.persistence.jpa.repository.InvoiceSubscriptionStateRepository;
import com.aratiri.payments.application.invoice.InvoiceProcessorService;
import com.aratiri.payments.domain.LightningInvoiceUpdate;
import com.google.protobuf.ByteString;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import lnrpc.Invoice;
import lnrpc.InvoiceSubscription;
import lnrpc.LightningGrpc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LightningListenerTest {

    @Mock
    private LightningGrpc.LightningStub lightningAsyncStub;
    @Mock
    private InvoiceProcessorService invoiceProcessorService;
    @Mock
    private InvoiceSubscriptionStateRepository invoiceSubscriptionStateRepository;
    @Mock
    private ClientCallStreamObserver<InvoiceSubscription> requestStream;

    private LightningListener listener;

    @BeforeEach
    void setUp() {
        listener = new LightningListener(
                lightningAsyncStub,
                invoiceProcessorService,
                invoiceSubscriptionStateRepository
        );
        when(invoiceSubscriptionStateRepository.findById("singleton"))
                .thenReturn(Optional.of(InvoiceSubscriptionState.builder()
                        .id("singleton")
                        .addIndex(10L)
                        .settleIndex(20L)
                        .build()));
    }

    @Test
    void processingFailure_cancelsClientStream_andIgnoresSubsequentOnNext() {
        AtomicInteger processed = new AtomicInteger();
        doAnswer(invocation -> {
            if (processed.getAndIncrement() == 0) {
                throw new RuntimeException("boom on N");
            }
            return null;
        }).when(invoiceProcessorService).processInvoiceUpdate(any());

        ArgumentCaptor<ClientResponseObserver<InvoiceSubscription, Invoice>> observerCaptor =
                ArgumentCaptor.forClass(ClientResponseObserver.class);
        doAnswer(invocation -> {
            ClientResponseObserver<InvoiceSubscription, Invoice> observer = invocation.getArgument(1);
            observer.beforeStart(requestStream);
            return null;
        }).when(lightningAsyncStub).subscribeInvoices(any(InvoiceSubscription.class), observerCaptor.capture());

        listener.subscribeToInvoices();

        ClientResponseObserver<InvoiceSubscription, Invoice> observer = observerCaptor.getValue();
        observer.onNext(invoice(11L, 21L, "lnbc-n"));
        observer.onNext(invoice(12L, 22L, "lnbc-n-plus-1"));

        verify(requestStream).cancel(eq("processing failure"), isNull());
        verify(invoiceProcessorService, times(1)).processInvoiceUpdate(any(LightningInvoiceUpdate.class));
        ArgumentCaptor<LightningInvoiceUpdate> updateCaptor = ArgumentCaptor.forClass(LightningInvoiceUpdate.class);
        verify(invoiceProcessorService).processInvoiceUpdate(updateCaptor.capture());
        assertEquals("lnbc-n", updateCaptor.getValue().paymentRequest());
    }

    @Test
    void reconnect_afterFailure_ignoresStaleObserverEvents_andOpensNewStream() {
        ClientCallStreamObserver<InvoiceSubscription> firstStream = mock(ClientCallStreamObserver.class);
        ClientCallStreamObserver<InvoiceSubscription> secondStream = mock(ClientCallStreamObserver.class);
        AtomicInteger starts = new AtomicInteger();
        ArgumentCaptor<ClientResponseObserver<InvoiceSubscription, Invoice>> observerCaptor =
                ArgumentCaptor.forClass(ClientResponseObserver.class);

        doAnswer(invocation -> {
            ClientResponseObserver<InvoiceSubscription, Invoice> observer = invocation.getArgument(1);
            observer.beforeStart(starts.getAndIncrement() == 0 ? firstStream : secondStream);
            return null;
        }).when(lightningAsyncStub).subscribeInvoices(any(InvoiceSubscription.class), observerCaptor.capture());

        doThrow(new RuntimeException("boom")).when(invoiceProcessorService).processInvoiceUpdate(any());

        listener.subscribeToInvoices();
        ClientResponseObserver<InvoiceSubscription, Invoice> firstObserver = observerCaptor.getValue();
        firstObserver.onNext(invoice(1L, 1L, "lnbc-fail"));
        verify(firstStream).cancel(eq("processing failure"), isNull());

        reset(invoiceProcessorService);
        listener.subscribeToInvoices();
        assertEquals(2, starts.get(), "reconnect must open a second subscribeInvoices stream");

        // Stale observer must not process N+1 after failure/reconnect.
        firstObserver.onNext(invoice(2L, 2L, "lnbc-stale"));
        verify(invoiceProcessorService, never()).processInvoiceUpdate(any());
    }

    private static Invoice invoice(long addIndex, long settleIndex, String paymentRequest) {
        return Invoice.newBuilder()
                .setPaymentRequest(paymentRequest)
                .setRHash(ByteString.copyFrom(new byte[32]))
                .setState(Invoice.InvoiceState.SETTLED)
                .setAmtPaidSat(100)
                .setAddIndex(addIndex)
                .setSettleIndex(settleIndex)
                .build();
    }
}
