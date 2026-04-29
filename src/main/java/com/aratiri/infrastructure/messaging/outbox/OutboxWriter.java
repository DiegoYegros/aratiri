package com.aratiri.infrastructure.messaging.outbox;

import com.aratiri.payments.application.event.PaymentInitiatedEvent;
import com.aratiri.invoices.application.event.InvoiceSettledEvent;
import com.aratiri.payments.application.event.OnChainPaymentInitiatedEvent;
import com.aratiri.payments.application.event.PaymentSentEvent;
import com.aratiri.transactions.application.event.InternalInvoiceCancelEvent;
import com.aratiri.transactions.application.event.InternalTransferCompletedEvent;
import com.aratiri.transactions.application.event.InternalTransferInitiatedEvent;
import com.aratiri.transactions.application.event.OnChainTransactionReceivedEvent;

public interface OutboxWriter {

    void publishInvoiceSettled(String invoiceId, InvoiceSettledEvent eventPayload);

    void publishPaymentInitiated(String transactionId, PaymentInitiatedEvent eventPayload);

    void publishOnChainPaymentInitiated(String transactionId, OnChainPaymentInitiatedEvent eventPayload);

    void publishInternalTransferInitiated(String transactionId, InternalTransferInitiatedEvent eventPayload);

    void publishPaymentSent(String transactionId, PaymentSentEvent eventPayload);

    void publishOnChainTransactionReceived(String referenceId, OnChainTransactionReceivedEvent eventPayload);

    void publishInternalTransferCompleted(String transactionId, InternalTransferCompletedEvent eventPayload);

    void publishInternalInvoiceCancel(String paymentHash, InternalInvoiceCancelEvent eventPayload);
}
