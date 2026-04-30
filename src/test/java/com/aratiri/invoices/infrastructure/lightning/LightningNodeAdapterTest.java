package com.aratiri.invoices.infrastructure.lightning;

import com.aratiri.invoices.domain.DecodedLightningInvoice;
import com.aratiri.invoices.domain.LightningInvoice;
import com.aratiri.invoices.domain.LightningInvoiceCreation;
import com.aratiri.invoices.domain.LightningNodeInvoice;
import com.aratiri.shared.exception.AratiriException;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lnrpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LightningNodeAdapterTest {

    @Mock
    private LightningGrpc.LightningBlockingStub lightningStub;

    private LightningNodeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LightningNodeAdapter(lightningStub);
    }

    @Test
    void createInvoice_returnsInvoiceCreation() {
        byte[] preimage = new byte[32];
        byte[] hash = new byte[32];
        String paymentRequest = "lnbc1...";

        when(lightningStub.addInvoice(any(Invoice.class)))
                .thenReturn(AddInvoiceResponse.newBuilder().setPaymentRequest(paymentRequest).build());
        when(lightningStub.decodePayReq(any(PayReqString.class)))
                .thenReturn(PayReq.newBuilder()
                        .setPaymentHash("hash")
                        .setExpiry(3600L)
                        .build());

        LightningInvoiceCreation result = adapter.createInvoice(5000L, "test memo", preimage, hash);

        assertEquals(paymentRequest, result.paymentRequest());
        assertEquals("hash", result.paymentHash());
        assertEquals(3600L, result.expiry());
    }

    @Test
    void createInvoice_throwsAratiriExceptionOnGrpcError() {
        when(lightningStub.addInvoice(any(Invoice.class)))
                .thenThrow(new StatusRuntimeException(Status.INTERNAL));

        assertThrows(AratiriException.class,
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
    void decodePaymentRequest_throwsAratiriExceptionOnGrpcError() {
        when(lightningStub.decodePayReq(any(PayReqString.class)))
                .thenThrow(new StatusRuntimeException(Status.INTERNAL));

        assertThrows(AratiriException.class,
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
    void lookupInvoice_throwsAratiriExceptionOnOtherGrpcError() {
        when(lightningStub.lookupInvoice(any(PaymentHash.class)))
                .thenThrow(new StatusRuntimeException(Status.INTERNAL));

        assertThrows(AratiriException.class,
                () -> adapter.lookupInvoice("deadbeef"));
    }
}
