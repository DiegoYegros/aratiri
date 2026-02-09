package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.transactions.application.event.InternalInvoiceCancelEvent;
import invoicesrpc.CancelInvoiceMsg;
import invoicesrpc.InvoicesGrpc;
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

    @KafkaListener(topics = "internal.invoice.cancel", groupId = "internal-invoice-cancel-group")
    public void handleInternalInvoiceCancel(String message, Acknowledgment acknowledgment) {
        try {
            InternalInvoiceCancelEvent event = jsonMapper.readValue(message, InternalInvoiceCancelEvent.class);
            CancelInvoiceMsg cancelInvoiceMsg = CancelInvoiceMsg.newBuilder()
                    .setPaymentHash(com.google.protobuf.ByteString.copyFrom(HexFormat.of().parseHex(event.getPaymentHash())))
                    .build();
            invoicesBlockingStub.cancelInvoice(cancelInvoiceMsg);
            acknowledgment.acknowledge();
            log.info("Canceled internal invoice for paymentHash: {}", event.getPaymentHash());
        } catch (Exception e) {
            log.error("Failed to cancel internal invoice: {}", message, e);
        }
    }
}
