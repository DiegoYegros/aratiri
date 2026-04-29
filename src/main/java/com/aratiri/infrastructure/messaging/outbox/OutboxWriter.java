package com.aratiri.infrastructure.messaging.outbox;

import com.aratiri.payments.application.event.PaymentInitiatedEvent;
import com.aratiri.payments.application.event.PaymentSentEvent;
import com.aratiri.transactions.application.event.InternalInvoiceCancelEvent;
import com.aratiri.transactions.application.event.InternalTransferCompletedEvent;

public interface OutboxWriter {

    void publishPaymentInitiated(String transactionId, PaymentInitiatedEvent eventPayload);

    void publishPaymentSent(String transactionId, PaymentSentEvent eventPayload);

    void publishInternalTransferCompleted(String transactionId, InternalTransferCompletedEvent eventPayload);

    void publishInternalInvoiceCancel(String paymentHash, InternalInvoiceCancelEvent eventPayload);
}
