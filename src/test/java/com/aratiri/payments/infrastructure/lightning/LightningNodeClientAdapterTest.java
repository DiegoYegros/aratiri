package com.aratiri.payments.infrastructure.lightning;

import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.domain.LightningPayment;
import com.aratiri.payments.domain.LightningPaymentStatus;
import com.aratiri.payments.domain.OnChainFeeEstimate;
import com.aratiri.payments.domain.exception.LightningNodeTransportException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lnrpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import routerrpc.RouterGrpc;
import routerrpc.SendPaymentRequest;
import routerrpc.TrackPaymentRequest;

import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LightningNodeClientAdapterTest {

    @Mock
    private RouterGrpc.RouterBlockingStub routerStub;

    @Mock
    private LightningGrpc.LightningBlockingStub lightningStub;

    private LightningNodeClientAdapter adapter;

    @BeforeEach
    void setUp() {
        lenient().when(routerStub.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(routerStub);
        lenient().when(lightningStub.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(lightningStub);
        adapter = new LightningNodeClientAdapter(routerStub, lightningStub);
    }

    @Test
    void executeLightningPayment_returnsSucceededPayment() {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1...");

        Payment payment = Payment.newBuilder()
                .setStatus(Payment.PaymentStatus.SUCCEEDED)
                .build();

        Iterator<Payment> iterator = new Iterator<>() {
            private boolean hasNext = true;

            @Override
            public boolean hasNext() {
                if (hasNext) {
                    hasNext = false;
                    return true;
                }
                return false;
            }

            @Override
            public Payment next() {
                return payment;
            }
        };

        when(routerStub.sendPaymentV2(any(SendPaymentRequest.class))).thenReturn(iterator);

        Optional<LightningPayment> result = adapter.executeLightningPayment(request, 5000, 30);

        assertTrue(result.isPresent());
        assertEquals(LightningPaymentStatus.SUCCEEDED, result.get().status());
    }

    @Test
    void executeLightningPayment_returnsFailedPayment() {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1...");
        Payment payment = Payment.newBuilder()
                .setStatus(Payment.PaymentStatus.FAILED)
                .setFailureReason(PaymentFailureReason.FAILURE_REASON_NO_ROUTE)
                .build();
        when(routerStub.sendPaymentV2(any(SendPaymentRequest.class))).thenReturn(java.util.List.of(payment).iterator());

        Optional<LightningPayment> result = adapter.executeLightningPayment(request, 5000, 30);

        assertTrue(result.isPresent());
        assertEquals(LightningPaymentStatus.FAILED, result.get().status());
        assertEquals("FAILURE_REASON_NO_ROUTE", result.get().failureReason());
    }

    @Test
    void executeLightningPayment_returnsEmptyWhenNoTerminalPayment() {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1...");
        Payment payment = Payment.newBuilder()
                .setStatus(Payment.PaymentStatus.IN_FLIGHT)
                .build();
        when(routerStub.sendPaymentV2(any(SendPaymentRequest.class))).thenReturn(java.util.List.of(payment).iterator());

        Optional<LightningPayment> result = adapter.executeLightningPayment(request, 5000, 30);

        assertTrue(result.isEmpty());
    }

    @Test
    void executeLightningPayment_wrapsTransportError() {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1...");
        when(routerStub.sendPaymentV2(any(SendPaymentRequest.class)))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThrows(LightningNodeTransportException.class, () -> adapter.executeLightningPayment(request, 5000, 30));
    }

    @Test
    void findPayment_returnsPaymentWhenFound() {
        Payment payment = Payment.newBuilder()
                .setStatus(Payment.PaymentStatus.SUCCEEDED)
                .build();

        Iterator<Payment> iterator = new Iterator<>() {
            private boolean hasNext = true;

            @Override
            public boolean hasNext() {
                if (hasNext) {
                    hasNext = false;
                    return true;
                }
                return false;
            }

            @Override
            public Payment next() {
                return payment;
            }
        };

        when(routerStub.trackPaymentV2(any(TrackPaymentRequest.class))).thenReturn(iterator);

        Optional<LightningPayment> result = adapter.findPayment("deadbeef");

        assertTrue(result.isPresent());
        assertEquals(LightningPaymentStatus.SUCCEEDED, result.get().status());
    }

    @Test
    void findPayment_returnsInFlightFromFirstUpdateWithoutConsumingMore() {
        Payment inFlight = Payment.newBuilder()
                .setStatus(Payment.PaymentStatus.IN_FLIGHT)
                .build();
        AtomicBoolean consumedPastFirst = new AtomicBoolean(false);
        Iterator<Payment> iterator = new Iterator<>() {
            private boolean firstPending = true;

            @Override
            public boolean hasNext() {
                if (!firstPending) {
                    consumedPastFirst.set(true);
                }
                return firstPending;
            }

            @Override
            public Payment next() {
                firstPending = false;
                return inFlight;
            }
        };
        when(routerStub.trackPaymentV2(any(TrackPaymentRequest.class))).thenReturn(iterator);

        Optional<LightningPayment> result = adapter.findPayment("deadbeef");

        assertTrue(result.isPresent());
        assertEquals(LightningPaymentStatus.IN_FLIGHT, result.get().status());
        assertFalse(consumedPastFirst.get(), "findPayment must not block waiting for terminal updates");

        org.mockito.ArgumentCaptor<TrackPaymentRequest> captor = org.mockito.ArgumentCaptor.forClass(TrackPaymentRequest.class);
        verify(routerStub).trackPaymentV2(captor.capture());
        assertFalse(captor.getValue().getNoInflightUpdates(), "must stream the current state immediately");
    }

    @Test
    void findPayment_returnsEmptyOnNotFound() {
        when(routerStub.trackPaymentV2(any(TrackPaymentRequest.class)))
                .thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        Optional<LightningPayment> result = adapter.findPayment("deadbeef");

        assertTrue(result.isEmpty());
    }

    @Test
    void findPayment_throwsOnOtherError() {
        when(routerStub.trackPaymentV2(any(TrackPaymentRequest.class)))
                .thenThrow(new StatusRuntimeException(Status.INTERNAL));

        assertThrows(LightningNodeTransportException.class, () -> adapter.findPayment("deadbeef"));
    }

    @Test
    void sendOnChain_returnsTxid() {
        OnChainPaymentDTOs.SendOnChainRequestDTO request = new OnChainPaymentDTOs.SendOnChainRequestDTO();
        request.setAddress("bc1q...");
        request.setSatsAmount(10000L);

        when(lightningStub.sendCoins(any(SendCoinsRequest.class)))
                .thenReturn(SendCoinsResponse.newBuilder().setTxid("txid123").build());

        String txid = adapter.sendOnChain(request);

        assertEquals("txid123", txid);
    }

    @Test
    void estimateOnChainFee_returnsEstimate() {
        OnChainPaymentDTOs.EstimateFeeRequestDTO request = new OnChainPaymentDTOs.EstimateFeeRequestDTO();
        request.setAddress("bc1q...");
        request.setSatsAmount(10000L);

        when(lightningStub.estimateFee(any(EstimateFeeRequest.class)))
                .thenReturn(EstimateFeeResponse.newBuilder().setFeeSat(200L).setSatPerVbyte(10L).build());

        OnChainFeeEstimate estimate = adapter.estimateOnChainFee(request);

        assertEquals(200L, estimate.feeSat());
        assertEquals(10L, estimate.satPerVbyte());
    }
}
