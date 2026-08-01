package com.aratiri.invoices.infrastructure.lightning;

import com.aratiri.invoices.domain.DecodedLightningInvoice;
import com.aratiri.invoices.domain.InvoiceCancelOutcome;
import com.aratiri.invoices.domain.LightningInvoice;
import com.aratiri.invoices.domain.LightningInvoiceCreation;
import com.aratiri.invoices.domain.LightningNodeInvoice;
import com.aratiri.errors.ApplicationException;
import com.google.protobuf.ByteString;
import invoicesrpc.CancelInvoiceMsg;
import invoicesrpc.CancelInvoiceResp;
import invoicesrpc.InvoicesGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lnrpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LightningNodeAdapterTest {

    @Mock
    private LightningGrpc.LightningBlockingStub lightningStub;

    @Mock
    private InvoicesGrpc.InvoicesBlockingStub invoicesStub;

    private LightningNodeAdapter adapter;

    @BeforeEach
    void setUp() {
        lenient().when(lightningStub.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(lightningStub);
        lenient().when(invoicesStub.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(invoicesStub);
        adapter = new LightningNodeAdapter(lightningStub, invoicesStub, 3600L);
    }

    @Test
    void createInvoice_returnsInvoiceCreationWithoutRedundantDecode() {
        byte[] preimage = new byte[32];
        byte[] hash = new byte[32];
        hash[31] = 1;
        String paymentRequest = "lnbc1...";

        when(lightningStub.addInvoice(any(Invoice.class)))
                .thenReturn(AddInvoiceResponse.newBuilder()
                        .setPaymentRequest(paymentRequest)
                        .setRHash(ByteString.copyFrom(hash))
                        .build());

        LightningInvoiceCreation result = adapter.createInvoice(5000L, "test memo", preimage, hash);

        assertEquals(paymentRequest, result.paymentRequest());
        assertEquals(java.util.HexFormat.of().formatHex(hash), result.paymentHash());
        assertEquals(3600L, result.expiry());
        org.mockito.Mockito.verify(lightningStub, org.mockito.Mockito.never())
                .decodePayReq(any(PayReqString.class));

        ArgumentCaptor<Invoice> requestCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(lightningStub).addInvoice(requestCaptor.capture());
        assertEquals(3600L, requestCaptor.getValue().getExpiry(), "expiry must be requested explicitly");
    }

    @Test
    void createInvoice_throwsApplicationExceptionOnGrpcError() {
        when(lightningStub.addInvoice(any(Invoice.class)))
                .thenThrow(new StatusRuntimeException(Status.INTERNAL));

        assertThrows(ApplicationException.class,
                () -> adapter.createInvoice(5000L, "test memo", new byte[32], new byte[32]));
    }

    @Test
    void decodePaymentRequest_returnsDecodedInvoice() {
        String paymentRequest = "lnbc1...";
        long timestamp = System.currentTimeMillis();
        byte[] paymentAddr = new byte[32];

        when(lightningStub.decodePayReq(any(PayReqString.class)))
                .thenReturn(PayReq.newBuilder()
                        .setPaymentHash("hash")
                        .setNumSatoshis(5000L)
                        .setDescription("test")
                        .setDescriptionHash("desc-hash")
                        .setExpiry(3600L)
                        .setDestination("dest")
                        .setCltvExpiry(144)
                        .setPaymentAddr(ByteString.copyFrom(paymentAddr))
                        .setTimestamp(timestamp)
                        .setFallbackAddr("bc1q...")
                        .build());

        DecodedLightningInvoice result = adapter.decodePaymentRequest(paymentRequest);

        assertEquals("hash", result.paymentHash());
        assertEquals(5000L, result.numSatoshis());
        assertEquals("test", result.description());
        assertEquals("dest", result.destination());
    }

    @Test
    void decodePaymentRequest_throwsApplicationExceptionOnGrpcError() {
        when(lightningStub.decodePayReq(any(PayReqString.class)))
                .thenThrow(new StatusRuntimeException(Status.INTERNAL));

        assertThrows(ApplicationException.class,
                () -> adapter.decodePaymentRequest("lnbc1..."));
    }

    @Test
    void lookupInvoice_returnsInvoiceWhenFound() {
        String paymentHash = "deadbeef";
        Invoice.InvoiceState openState = Invoice.InvoiceState.OPEN;

        when(lightningStub.lookupInvoice(any(PaymentHash.class)))
                .thenReturn(Invoice.newBuilder()
                        .setPaymentRequest("lnbc1...")
                        .setState(openState)
                        .setAmtPaidSat(0L)
                        .setValue(5000L)
                        .build());

        Optional<LightningNodeInvoice> result = adapter.lookupInvoice(paymentHash);

        assertTrue(result.isPresent());
        assertEquals("lnbc1...", result.get().paymentRequest());
        assertEquals(LightningInvoice.InvoiceState.OPEN, result.get().state());
    }

    @Test
    void lookupInvoice_returnsEmptyWhenNotFound() {
        when(lightningStub.lookupInvoice(any(PaymentHash.class)))
                .thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        Optional<LightningNodeInvoice> result = adapter.lookupInvoice("deadbeef");

        assertTrue(result.isEmpty());
    }

    @Test
    void lookupInvoice_throwsApplicationExceptionOnOtherGrpcError() {
        when(lightningStub.lookupInvoice(any(PaymentHash.class)))
                .thenThrow(new StatusRuntimeException(Status.INTERNAL));

        assertThrows(ApplicationException.class,
                () -> adapter.lookupInvoice("deadbeef"));
    }

    @Test
    void cancelInvoice_succeedsViaInvoicesRpc() {
        String paymentHash = "aabb";
        when(invoicesStub.cancelInvoice(any(CancelInvoiceMsg.class)))
                .thenReturn(CancelInvoiceResp.getDefaultInstance());

        InvoiceCancelOutcome outcome = adapter.cancelInvoice(paymentHash);

        assertEquals(InvoiceCancelOutcome.CANCELLED, outcome);
        ArgumentCaptor<CancelInvoiceMsg> captor = ArgumentCaptor.forClass(CancelInvoiceMsg.class);
        verify(invoicesStub).cancelInvoice(captor.capture());
        assertArrayEquals(ByteString.fromHex(paymentHash).toByteArray(), captor.getValue().getPaymentHash().toByteArray());
    }

    @Test
    void cancelInvoice_mapsAlreadySettled() {
        when(invoicesStub.cancelInvoice(any(CancelInvoiceMsg.class)))
                .thenThrow(new StatusRuntimeException(Status.FAILED_PRECONDITION.withDescription("invoice already settled")));

        assertEquals(InvoiceCancelOutcome.ALREADY_SETTLED, adapter.cancelInvoice("deadbeef"));
    }

    @Test
    void cancelInvoice_mapsNotFound() {
        when(invoicesStub.cancelInvoice(any(CancelInvoiceMsg.class)))
                .thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        assertEquals(InvoiceCancelOutcome.NOT_FOUND, adapter.cancelInvoice("deadbeef"));
    }

    @Test
    void cancelInvoice_throwsApplicationExceptionOnOtherGrpcError() {
        when(invoicesStub.cancelInvoice(any(CancelInvoiceMsg.class)))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        ApplicationException ex = assertThrows(ApplicationException.class, () -> adapter.cancelInvoice("deadbeef"));
        assertEquals(502, ex.getStatus());
    }
}
