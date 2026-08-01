package com.aratiri.invoices.infrastructure.lightning;

import com.aratiri.infrastructure.grpc.GrpcDeadlines;
import com.aratiri.invoices.application.port.out.LightningNodePort;
import com.aratiri.invoices.domain.DecodedLightningInvoice;
import com.aratiri.invoices.domain.InvoiceCancelOutcome;
import com.aratiri.invoices.domain.LightningInvoice;
import com.aratiri.invoices.domain.LightningInvoiceCreation;
import com.aratiri.invoices.domain.LightningNodeInvoice;
import com.aratiri.errors.ApplicationException;
import com.google.protobuf.ByteString;
import invoicesrpc.CancelInvoiceMsg;
import invoicesrpc.InvoicesGrpc;
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
    private final InvoicesGrpc.InvoicesBlockingStub invoicesStub;
    private final long expirySeconds;

    public LightningNodeAdapter(
            LightningGrpc.LightningBlockingStub lightningStub,
            InvoicesGrpc.InvoicesBlockingStub invoicesStub,
            @Value("${aratiri.invoices.expiry-seconds:3600}") long expirySeconds
    ) {
        this.lightningStub = lightningStub;
        this.invoicesStub = invoicesStub;
        this.expirySeconds = expirySeconds;
    }

    @Override
    public LightningInvoiceCreation createInvoice(long satsAmount, String memo, byte[] preimage, byte[] hash) {
        return createInvoice(satsAmount, memo, preimage, hash, expirySeconds);
    }

    @Override
    public LightningInvoiceCreation createInvoice(
            long satsAmount,
            String memo,
            byte[] preimage,
            byte[] hash,
            long expirySeconds
    ) {
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
            throw new ApplicationException("Error creating invoice on LND node: " + e.getMessage(), HttpStatus.BAD_GATEWAY.value());
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
            throw new ApplicationException("Error decoding payment request: " + e.getMessage(), HttpStatus.BAD_GATEWAY.value());
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
            throw new ApplicationException("Error looking up invoice on LND node: " + e.getMessage(), HttpStatus.BAD_GATEWAY.value());
        }
    }

    @Override
    public InvoiceCancelOutcome cancelInvoice(String paymentHash) {
        try {
            CancelInvoiceMsg request = CancelInvoiceMsg.newBuilder()
                    .setPaymentHash(ByteString.fromHex(paymentHash))
                    .build();
            invoicesStub
                    .withDeadlineAfter(GrpcDeadlines.INVOICE_MUTATION.toMillis(), TimeUnit.MILLISECONDS)
                    .cancelInvoice(request);
            return InvoiceCancelOutcome.CANCELLED;
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.NOT_FOUND) {
                // Invoice absent on this node: BOLT11 is not payable here.
                return InvoiceCancelOutcome.NOT_FOUND;
            }
            if (code == Status.Code.FAILED_PRECONDITION) {
                // LND: CancelInvoice fails when the invoice is already settled.
                return InvoiceCancelOutcome.ALREADY_SETTLED;
            }
            throw new ApplicationException(
                    "Error cancelling invoice on LND node: " + e.getMessage(),
                    HttpStatus.BAD_GATEWAY.value()
            );
        }
    }
}
