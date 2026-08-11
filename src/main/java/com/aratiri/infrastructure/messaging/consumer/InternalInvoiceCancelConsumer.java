package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.infrastructure.messaging.KafkaTopicNames;
import com.aratiri.transactions.application.event.InternalInvoiceCancelEvent;
import com.google.protobuf.ByteString;
import invoicesrpc.CancelInvoiceMsg;
import invoicesrpc.InvoicesGrpc;
import invoicesrpc.LookupInvoiceMsg;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.HexFormat;

@Component
@RequiredArgsConstructor
@Slf4j
public class InternalInvoiceCancelConsumer {

    private final InvoicesGrpc.InvoicesBlockingStub invoicesBlockingStub;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = KafkaTopicNames.INTERNAL_INVOICE_CANCEL, groupId = "internal-invoice-cancel-group")
    public void handleInternalInvoiceCancel(String message, Acknowledgment acknowledgment) {
        try {
            InternalInvoiceCancelEvent event = jsonMapper.readValue(message, InternalInvoiceCancelEvent.class);
            if (isAlreadyTerminalOnNode(event.getPaymentHash())) {
                log.info("Internal invoice {} is already settled or canceled on the node; skipping cancel.", event.getPaymentHash());
                acknowledgment.acknowledge();
                return;
            }
            CancelInvoiceMsg cancelInvoiceMsg = CancelInvoiceMsg.newBuilder()
                    .setPaymentHash(ByteString.copyFrom(HexFormat.of().parseHex(event.getPaymentHash())))
                    .build();
            invoicesBlockingStub.cancelInvoice(cancelInvoiceMsg);
            acknowledgment.acknowledge();
            log.info("Canceled internal invoice for paymentHash: {}", event.getPaymentHash());
        } catch (Exception e) {
            log.error("Failed to cancel internal invoice: {}", message, e);
            throw new IllegalStateException("internal.invoice.cancel processing failed", e);
        }
    }

    /**
     * Idempotency guard for redelivered cancel events: canceling an already-canceled invoice is a
     * no-op on LND, but canceling a settled invoice fails. Skipping terminal invoices keeps
     * duplicate consumption from erroring into the dead-letter topic.
     */
    private boolean isAlreadyTerminalOnNode(String paymentHash) {
        try {
            LookupInvoiceMsg lookup = LookupInvoiceMsg.newBuilder()
                    .setPaymentHash(ByteString.copyFrom(HexFormat.of().parseHex(paymentHash)))
                    .build();
            lnrpc.Invoice invoice = invoicesBlockingStub.lookupInvoiceV2(lookup);
            return invoice.getState() == lnrpc.Invoice.InvoiceState.SETTLED
                    || invoice.getState() == lnrpc.Invoice.InvoiceState.CANCELED;
        } catch (Exception e) {
            log.warn("Could not look up invoice {} before cancel; proceeding with cancel.", paymentHash, e);
            return false;
        }
    }
}
