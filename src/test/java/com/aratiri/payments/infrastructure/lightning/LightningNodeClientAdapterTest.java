package com.aratiri.payments.infrastructure.lightning;

import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.domain.OnChainFeeEstimate;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

        Payment result = adapter.executeLightningPayment(request, 5000, 30);

        assertEquals(Payment.PaymentStatus.SUCCEEDED, result.getStatus());
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

        Optional<Payment> result = adapter.findPayment("deadbeef");

        assertTrue(result.isPresent());
        assertEquals(Payment.PaymentStatus.SUCCEEDED, result.get().getStatus());
    }

    @Test
    void findPayment_returnsEmptyOnNotFound() {
        when(routerStub.trackPaymentV2(any(TrackPaymentRequest.class)))
                .thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        Optional<Payment> result = adapter.findPayment("deadbeef");

        assertTrue(result.isEmpty());
    }

    @Test
    void findPayment_throwsOnOtherError() {
        when(routerStub.trackPaymentV2(any(TrackPaymentRequest.class)))
                .thenThrow(new StatusRuntimeException(Status.INTERNAL));

        assertThrows(StatusRuntimeException.class, () -> adapter.findPayment("deadbeef"));
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
