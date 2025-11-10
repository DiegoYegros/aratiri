package com.aratiri.invoices.infrastructure.lightning;

import com.aratiri.invoices.application.port.out.LightningNodePort;
import com.aratiri.invoices.domain.DecodedLightningInvoice;
import com.aratiri.invoices.domain.LightningInvoice;
import com.aratiri.invoices.domain.LightningInvoiceCreation;
import com.aratiri.invoices.domain.LightningNodeInvoice;
import com.aratiri.shared.exception.AratiriException;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lnrpc.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component("invoicesLightningNodeAdapter")
public class LightningNodeAdapter implements LightningNodePort {

    private final LightningGrpc.LightningBlockingStub lightningStub;

    public LightningNodeAdapter(LightningGrpc.LightningBlockingStub lightningStub) {
        this.lightningStub = lightningStub;
    }

    @Override
    public LightningInvoiceCreation createInvoice(long satsAmount, String memo, byte[] preimage, byte[] hash) {
        try {
            Invoice request = Invoice.newBuilder()
                    .setRHash(ByteString.copyFrom(hash))
                    .setMemo(memo)
                    .setRPreimage(ByteString.copyFrom(preimage))
                    .setValue(satsAmount)
                    .build();
            AddInvoiceResponse response = lightningStub.addInvoice(request);
            PayReq payReq = lightningStub.decodePayReq(
                    PayReqString.newBuilder().setPayReq(response.getPaymentRequest()).build()
            );
            return new LightningInvoiceCreation(
                    response.getPaymentRequest(),
                    payReq.getPaymentHash(),
                    payReq.getExpiry()
            );
        } catch (StatusRuntimeException e) {
            throw new AratiriException("Error creating invoice on LND node: " + e.getMessage(), HttpStatus.BAD_GATEWAY.value());
        }
    }

    @Override
    public DecodedLightningInvoice decodePaymentRequest(String paymentRequest) {
        try {
            PayReq payReq = lightningStub.decodePayReq(
                    PayReqString.newBuilder().setPayReq(paymentRequest).build()
            );
            return new DecodedLightningInvoice(
                    payReq.getPaymentHash(),
                    payReq.getNumSatoshis(),
                    payReq.getDescription(),
                    payReq.getDescriptionHash(),
                    payReq.getExpiry(),
                    payReq.getDestination(),
                    payReq.getCltvExpiry(),
                    payReq.getPaymentAddr().toStringUtf8(),
                    Instant.ofEpochMilli(payReq.getTimestamp()),
                    payReq.getFallbackAddr(),
                    List.of()
            );
        } catch (StatusRuntimeException e) {
            throw new AratiriException("Error decoding payment request: " + e.getMessage(), HttpStatus.BAD_GATEWAY.value());
        }
    }

    @Override
    public Optional<LightningNodeInvoice> lookupInvoice(String paymentHash) {
        try {
            PaymentHash request = PaymentHash.newBuilder()
                    .setRHash(ByteString.fromHex(paymentHash))
                    .build();
            Invoice invoice = lightningStub.lookupInvoice(request);
            LightningInvoice.InvoiceState state = LightningInvoice.InvoiceState.valueOf(invoice.getState().name());
            return Optional.of(new LightningNodeInvoice(
                    invoice.getPaymentRequest(),
                    state,
                    invoice.getAmtPaidSat(),
                    invoice.getValue()
            ));
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            throw new AratiriException("Error looking up invoice on LND node: " + e.getMessage(), HttpStatus.BAD_GATEWAY.value());
        }
    }
}
