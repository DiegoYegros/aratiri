package com.aratiri.payments.infrastructure.lightning;

import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.port.out.LightningNodePort;
import com.aratiri.payments.domain.LightningPayment;
import com.aratiri.payments.domain.LightningPaymentStatus;
import com.aratiri.payments.domain.OnChainFeeEstimate;
import com.aratiri.payments.domain.exception.LightningNodeTransportException;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lnrpc.*;
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
    public Optional<LightningPayment> executeLightningPayment(PayInvoiceRequestDTO request, int defaultFeeLimitSat, int defaultTimeoutSeconds) {
        SendPaymentRequest grpcRequest = SendPaymentRequest.newBuilder()
                .setPaymentRequest(request.getInvoice())
                .setFeeLimitSat(request.getFeeLimitSat() != null ? request.getFeeLimitSat() : defaultFeeLimitSat)
                .setTimeoutSeconds(request.getTimeoutSeconds() != null ? request.getTimeoutSeconds() : defaultTimeoutSeconds)
                .setAllowSelfPayment(false)
                .build();
        try {
            Iterator<Payment> paymentStream = routerStub.sendPaymentV2(grpcRequest);
            while (paymentStream.hasNext()) {
                Payment payment = paymentStream.next();
                if (payment.getStatus() == Payment.PaymentStatus.SUCCEEDED ||
                        payment.getStatus() == Payment.PaymentStatus.FAILED) {
                    return Optional.of(toDomain(payment));
                }
            }
            return Optional.empty();
        } catch (StatusRuntimeException e) {
            throw toTransportException("send payment", e);
        }
    }

    @Override
    public Optional<LightningPayment> findPayment(String paymentHash) {
        try {
            TrackPaymentRequest request = TrackPaymentRequest.newBuilder()
                    .setPaymentHash(ByteString.fromHex(paymentHash))
                    .setNoInflightUpdates(true)
                    .build();
            Iterator<Payment> response = routerStub.trackPaymentV2(request);
            if (response.hasNext()) {
                return Optional.of(toDomain(response.next()));
            }
            return Optional.empty();
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            throw toTransportException("track payment", e);
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

    private LightningPayment toDomain(Payment payment) {
        return new LightningPayment(
                toDomainStatus(payment.getStatus()),
                payment.getFailureReason().toString(),
                payment.getFeeSat(),
                payment.getFeeMsat()
        );
    }

    private LightningPaymentStatus toDomainStatus(Payment.PaymentStatus status) {
        return switch (status) {
            case INITIATED -> LightningPaymentStatus.INITIATED;
            case IN_FLIGHT -> LightningPaymentStatus.IN_FLIGHT;
            case SUCCEEDED -> LightningPaymentStatus.SUCCEEDED;
            case FAILED -> LightningPaymentStatus.FAILED;
            case UNRECOGNIZED -> LightningPaymentStatus.UNKNOWN;
            default -> LightningPaymentStatus.UNKNOWN;
        };
    }

    private LightningNodeTransportException toTransportException(String operation, StatusRuntimeException e) {
        return new LightningNodeTransportException("LND " + operation + " failed: " + e.getMessage(), e);
    }
}
