package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.invoices.application.InvoiceSettlementFacts;
import com.aratiri.invoices.application.event.InvoiceSettledEvent;
import com.aratiri.invoices.application.port.in.InvoiceSettlementPort;
import com.aratiri.transactions.application.InvoiceCreditSettlement;
import com.aratiri.transactions.application.TransactionSettlementModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceSettledConsumerTest {

    @Mock
    private TransactionSettlementModule transactionSettlementModule;

    @Mock
    private InvoiceSettlementPort invoiceSettlementPort;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private Acknowledgment acknowledgment;

    private InvoiceSettledConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new InvoiceSettledConsumer(transactionSettlementModule, invoiceSettlementPort, jsonMapper);
    }

    @Test
    void handleInvoiceSettled_settlesInvoiceCreditThroughSettlementModule() throws Exception {
        String message = "{\"userId\":\"user-1\",\"amount\":2100,\"paymentHash\":\"payment-hash-1234567890\"}";
        InvoiceSettledEvent event = new InvoiceSettledEvent(
                "user-1",
                2100L,
                "payment-hash-1234567890",
                LocalDateTime.now(),
                "event memo"
        );

        when(jsonMapper.readValue(message, InvoiceSettledEvent.class)).thenReturn(event);
        when(invoiceSettlementPort.settlementFacts("payment-hash-1234567890"))
                .thenReturn(new InvoiceSettlementFacts(
                        "payment-hash-1234567890",
                        "Invoice memo",
                        "Invoice memo",
                        "external-invoice-ref",
                        "{\"order_id\":\"1001\"}"
                ));

        consumer.handleInvoiceSettled(message, "invoice.settled", 0, 10L, acknowledgment);

        ArgumentCaptor<InvoiceCreditSettlement> captor = ArgumentCaptor.forClass(InvoiceCreditSettlement.class);
        verify(transactionSettlementModule).settleInvoiceCredit(captor.capture());
        assertEquals("user-1", captor.getValue().userId());
        assertEquals(2100L, captor.getValue().amountSat());
        assertEquals("payment-hash-1234567890", captor.getValue().paymentHash());
        assertEquals("Invoice memo", captor.getValue().description());
        assertEquals("external-invoice-ref", captor.getValue().externalReference());
        assertEquals("{\"order_id\":\"1001\"}", captor.getValue().metadata());
        verify(invoiceSettlementPort).settlementFacts("payment-hash-1234567890");
        verify(acknowledgment).acknowledge();
    }
}
