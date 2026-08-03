package com.aratiri.payments.application.invoice;

import com.aratiri.infrastructure.messaging.outbox.OutboxWriter;
import com.aratiri.infrastructure.persistence.jpa.entity.InvoiceSubscriptionState;
import com.aratiri.infrastructure.persistence.jpa.repository.InvoiceSubscriptionStateRepository;
import com.aratiri.invoices.application.InvoiceSettledPublication;
import com.aratiri.invoices.application.InvoiceStateUpdate;
import com.aratiri.invoices.application.InvoiceStateUpdateResult;
import com.aratiri.invoices.application.event.InvoiceSettledEvent;
import com.aratiri.invoices.application.port.in.InvoiceSettlementPort;
import com.aratiri.payments.domain.LightningInvoiceUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceProcessorServiceTest {

    @Mock
    private InvoiceSettlementPort invoiceSettlementPort;

    @Mock
    private OutboxWriter outboxWriter;

    @Mock
    private InvoiceSubscriptionStateRepository invoiceSubscriptionStateRepository;

    private InvoiceProcessorService invoiceProcessorService;

    @BeforeEach
    void setUp() {
        invoiceProcessorService = new InvoiceProcessorService(
                invoiceSettlementPort,
                outboxWriter,
                invoiceSubscriptionStateRepository
        );
    }

    @Test
    void processInvoiceUpdate_settledInvoiceDelegatesOutboxDetailsToWriter() {
        LightningInvoiceUpdate invoice = new LightningInvoiceUpdate(
                "lnbc1settled",
                "payment-hash",
                LightningInvoiceUpdate.State.SETTLED,
                2_500L,
                10L,
                20L
        );
        InvoiceSettledEvent settledEvent = new InvoiceSettledEvent(
                "user-123",
                2_500L,
                "payment-hash",
                LocalDateTime.now(),
                "invoice memo"
        );
        when(invoiceSettlementPort.recordInvoiceStateUpdate(new InvoiceStateUpdate(
                "lnbc1settled",
                "payment-hash",
                InvoiceStateUpdate.State.SETTLED,
                2_500L
        ))).thenReturn(InvoiceStateUpdateResult.settled(
                new InvoiceSettledPublication("invoice-123", settledEvent)
        ));
        when(invoiceSubscriptionStateRepository.findById("singleton"))
                .thenReturn(Optional.of(InvoiceSubscriptionState.builder().id("singleton").addIndex(0).settleIndex(0).build()));

        invoiceProcessorService.processInvoiceUpdate(invoice);

        ArgumentCaptor<InvoiceStateUpdate> updateCaptor = ArgumentCaptor.forClass(InvoiceStateUpdate.class);
        verify(invoiceSettlementPort).recordInvoiceStateUpdate(updateCaptor.capture());
        assertEquals("lnbc1settled", updateCaptor.getValue().paymentRequest());
        assertEquals("payment-hash", updateCaptor.getValue().paymentHash());
        assertEquals(InvoiceStateUpdate.State.SETTLED, updateCaptor.getValue().state());
        assertEquals(2_500L, updateCaptor.getValue().amountPaidSat());

        ArgumentCaptor<InvoiceSettledEvent> eventCaptor = ArgumentCaptor.forClass(InvoiceSettledEvent.class);
        verify(outboxWriter).publishInvoiceSettled(eq("invoice-123"), eventCaptor.capture());
        InvoiceSettledEvent event = eventCaptor.getValue();
        assertEquals("user-123", event.getUserId());
        assertEquals(2_500L, event.getAmount());
        assertEquals("payment-hash", event.getPaymentHash());
        assertEquals("invoice memo", event.getMemo());
        verify(invoiceSubscriptionStateRepository).save(argThat(state ->
                state.getAddIndex() == 10L && state.getSettleIndex() == 20L
        ));
    }

    @Test
    void processInvoiceUpdate_ignoredDuplicateStillAdvancesCursorMonotonically() {
        LightningInvoiceUpdate invoice = new LightningInvoiceUpdate(
                "lnbc1",
                "hash-1",
                LightningInvoiceUpdate.State.CANCELED,
                0L,
                5L,
                0L
        );
        when(invoiceSettlementPort.recordInvoiceStateUpdate(any())).thenReturn(InvoiceStateUpdateResult.ignored());
        when(invoiceSubscriptionStateRepository.findById("singleton"))
                .thenReturn(Optional.of(InvoiceSubscriptionState.builder().id("singleton").addIndex(3).settleIndex(0).build()));

        invoiceProcessorService.processInvoiceUpdate(invoice);

        verify(outboxWriter, never()).publishInvoiceSettled(anyString(), any());
        verify(invoiceSubscriptionStateRepository).save(argThat(state ->
                state.getAddIndex() == 5L && state.getSettleIndex() == 0L
        ));
    }

    @Test
    void processInvoiceUpdate_failureBeforeCursorPreventsAdvance() {
        LightningInvoiceUpdate invoice = new LightningInvoiceUpdate(
                "lnbc1",
                "hash-1",
                LightningInvoiceUpdate.State.SETTLED,
                100L,
                11L,
                21L
        );
        when(invoiceSettlementPort.recordInvoiceStateUpdate(any()))
                .thenThrow(new RuntimeException("db failure"));

        assertThrows(RuntimeException.class, () -> invoiceProcessorService.processInvoiceUpdate(invoice));
        verify(invoiceSubscriptionStateRepository, never()).save(any());
    }
}
