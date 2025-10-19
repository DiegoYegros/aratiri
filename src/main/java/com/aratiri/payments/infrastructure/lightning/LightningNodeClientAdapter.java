package com.aratiri.payments.infrastructure.lightning;

import com.aratiri.dto.payments.OnChainPaymentDTOs;
import com.aratiri.dto.payments.PayInvoiceRequestDTO;
import com.aratiri.payments.application.port.out.LightningNodePort;
import com.aratiri.payments.domain.OnChainFeeEstimate;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lnrpc.EstimateFeeRequest;
import lnrpc.EstimateFeeResponse;
import lnrpc.LightningGrpc;
import lnrpc.Payment;
import lnrpc.SendCoinsRequest;
import lnrpc.SendCoinsResponse;
import org.springframework.stereotype.Component;
import routerrpc.RouterGrpc;
import routerrpc.SendPaymentRequest;
import routerrpc.TrackPaymentRequest;

import java.util.Iterator;
import java.util.Optional;

@Component
public class LightningNodeClientAdapter implements LightningNodePort {

    private final RouterGrpc.RouterBlockingStub routerStub;
    private final LightningGrpc.LightningBlockingStub lightningStub;

    public LightningNodeClientAdapter(
            RouterGrpc.RouterBlockingStub routerStub,
            LightningGrpc.LightningBlockingStub lightningStub
    ) {
        this.routerStub = routerStub;
        this.lightningStub = lightningStub;
    }

    @Override
    public Payment executeLightningPayment(PayInvoiceRequestDTO request, int defaultFeeLimitSat, int defaultTimeoutSeconds) {
        SendPaymentRequest grpcRequest = SendPaymentRequest.newBuilder()
                .setPaymentRequest(request.getInvoice())
                .setFeeLimitSat(request.getFeeLimitSat() != null ? request.getFeeLimitSat() : defaultFeeLimitSat)
                .setTimeoutSeconds(request.getTimeoutSeconds() != null ? request.getTimeoutSeconds() : defaultTimeoutSeconds)
                .setAllowSelfPayment(false)
                .build();
        Iterator<Payment> paymentStream = routerStub.sendPaymentV2(grpcRequest);
        Payment finalPayment = null;
        while (paymentStream.hasNext()) {
            Payment payment = paymentStream.next();
            if (payment.getStatus() == Payment.PaymentStatus.SUCCEEDED ||
                    payment.getStatus() == Payment.PaymentStatus.FAILED) {
                finalPayment = payment;
                break;
            }
        }
        return finalPayment;
    }

    @Override
    public Optional<Payment> findPayment(String paymentHash) {
        try {
            TrackPaymentRequest request = TrackPaymentRequest.newBuilder()
                    .setPaymentHash(ByteString.fromHex(paymentHash))
                    .setNoInflightUpdates(true)
                    .build();
            Iterator<Payment> response = routerStub.trackPaymentV2(request);
            if (response.hasNext()) {
                return Optional.of(response.next());
            }
            return Optional.empty();
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public String sendOnChain(OnChainPaymentDTOs.SendOnChainRequestDTO request) {
        SendCoinsRequest.Builder grpcRequestBuilder = SendCoinsRequest.newBuilder()
                .setAddr(request.getAddress())
                .setAmount(request.getSatsAmount());
        if (request.getSatPerVbyte() != null) {
            grpcRequestBuilder.setSatPerVbyte(request.getSatPerVbyte());
        } else if (request.getTargetConf() != null) {
            grpcRequestBuilder.setTargetConf(request.getTargetConf());
        }
        SendCoinsResponse response = lightningStub.sendCoins(grpcRequestBuilder.build());
        return response.getTxid();
    }

    @Override
    public OnChainFeeEstimate estimateOnChainFee(OnChainPaymentDTOs.EstimateFeeRequestDTO request) {
        EstimateFeeRequest.Builder grpcRequestBuilder = EstimateFeeRequest.newBuilder()
                .putAddrToAmount(request.getAddress(), request.getSatsAmount());
        if (request.getTargetConf() != null) {
            grpcRequestBuilder.setTargetConf(request.getTargetConf());
        } else {
            grpcRequestBuilder.setTargetConf(1);
        }
        EstimateFeeResponse response = lightningStub.estimateFee(grpcRequestBuilder.build());
        return new OnChainFeeEstimate(response.getFeeSat(), response.getSatPerVbyte());
    }
}
