package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.transactions.application.event.InternalInvoiceCancelEvent;
import invoicesrpc.CancelInvoiceMsg;
import invoicesrpc.InvoicesGrpc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalInvoiceCancelConsumerTest {

    @Mock
    private InvoicesGrpc.InvoicesBlockingStub invoicesBlockingStub;

    @Mock
    private Acknowledgment acknowledgment;

    private InternalInvoiceCancelConsumer consumer;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        jsonMapper = new JsonMapper();
        consumer = new InternalInvoiceCancelConsumer(invoicesBlockingStub, jsonMapper);
    }

    @Test
    void handleInternalInvoiceCancel_cancelsInvoice() throws Exception {
        InternalInvoiceCancelEvent event = new InternalInvoiceCancelEvent();
        event.setPaymentHash("deadbeef");
        String message = jsonMapper.writeValueAsString(event);

        when(invoicesBlockingStub.cancelInvoice(any(CancelInvoiceMsg.class)))
                .thenReturn(invoicesrpc.CancelInvoiceResp.getDefaultInstance());

        consumer.handleInternalInvoiceCancel(message, acknowledgment);

        verify(acknowledgment).acknowledge();
        verify(invoicesBlockingStub).cancelInvoice(any(CancelInvoiceMsg.class));
    }

    @Test
    void handleInternalInvoiceCancel_throwsOnInvalidJson() {
        String message = "invalid json";

        assertThrows(IllegalStateException.class,
                () -> consumer.handleInternalInvoiceCancel(message, acknowledgment));
    }

    @Test
    void handleInternalInvoiceCancel_throwsOnGrpcFailure() throws Exception {
        InternalInvoiceCancelEvent event = new InternalInvoiceCancelEvent();
        event.setPaymentHash("deadbeef");
        String message = jsonMapper.writeValueAsString(event);

        when(invoicesBlockingStub.cancelInvoice(any(CancelInvoiceMsg.class)))
                .thenThrow(new RuntimeException("gRPC error"));

        assertThrows(IllegalStateException.class,
                () -> consumer.handleInternalInvoiceCancel(message, acknowledgment));

        verify(acknowledgment, never()).acknowledge();
    }
}
