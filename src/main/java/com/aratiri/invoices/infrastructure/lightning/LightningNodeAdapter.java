package com.aratiri.invoices.infrastructure.lightning;

import com.aratiri.infrastructure.grpc.GrpcDeadlines;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component("invoicesLightningNodeAdapter")
public class LightningNodeAdapter implements LightningNodePort {

    private final LightningGrpc.LightningBlockingStub lightningStub;
    private final long expirySeconds;

    public LightningNodeAdapter(
            LightningGrpc.LightningBlockingStub lightningStub,
            @Value("${aratiri.invoices.expiry-seconds:3600}") long expirySeconds
    ) {
        this.lightningStub = lightningStub;
        this.expirySeconds = expirySeconds;
    }

    @Override
    public LightningInvoiceCreation createInvoice(long satsAmount, String memo, byte[] preimage, byte[] hash) {
        try {
            Invoice request = Invoice.newBuilder()
                    .setRHash(ByteString.copyFrom(hash))
                    .setMemo(memo)
                    .setRPreimage(ByteString.copyFrom(preimage))
                    .setValue(satsAmount)
                    .setExpiry(expirySeconds)
                    .build();
            AddInvoiceResponse response = lightningStub
                    .withDeadlineAfter(GrpcDeadlines.INVOICE_MUTATION.toMillis(), TimeUnit.MILLISECONDS)
                    .addInvoice(request);
            // Expiry is requested explicitly above and the r-hash is echoed in the response,
            // so no decodePayReq round-trip is needed here.
            return new LightningInvoiceCreation(
                    response.getPaymentRequest(),
                    HexFormat.of().formatHex(response.getRHash().toByteArray()),
                    expirySeconds
            );
        } catch (StatusRuntimeException e) {
            throw new AratiriException("Error creating invoice on LND node: " + e.getMessage(), HttpStatus.BAD_GATEWAY.value());
        }
    }

    // BOLT11 decode is deterministic per invoice string — safe to cache.
    @Override
    @Cacheable(cacheNames = "bolt11Decode", key = "#paymentRequest.toLowerCase()", sync = true)
    public DecodedLightningInvoice decodePaymentRequest(String paymentRequest) {
        try {
            PayReq payReq = lightningStub
                    .withDeadlineAfter(GrpcDeadlines.LOOKUP.toMillis(), TimeUnit.MILLISECONDS)
                    .decodePayReq(PayReqString.newBuilder().setPayReq(paymentRequest).build());
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
            Invoice invoice = lightningStub
                    .withDeadlineAfter(GrpcDeadlines.LOOKUP.toMillis(), TimeUnit.MILLISECONDS)
                    .lookupInvoice(request);
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
