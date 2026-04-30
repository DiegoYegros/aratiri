package com.aratiri.payments.application.invoice;

import com.aratiri.infrastructure.messaging.outbox.OutboxWriter;
import com.aratiri.infrastructure.persistence.jpa.entity.InvoiceSubscriptionState;
import com.aratiri.infrastructure.persistence.jpa.entity.LightningInvoiceEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.InvoiceSubscriptionStateRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.LightningInvoiceRepository;
import com.aratiri.invoices.application.event.InvoiceSettledEvent;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceProcessorServiceTest {

    @Mock
    private LightningInvoiceRepository lightningInvoiceRepository;

    @Mock
    private OutboxWriter outboxWriter;

    @Mock
    private InvoiceSubscriptionStateRepository invoiceSubscriptionStateRepository;

    private InvoiceProcessorService invoiceProcessorService;

    @BeforeEach
    void setUp() {
        invoiceProcessorService = new InvoiceProcessorService(
                lightningInvoiceRepository,
                outboxWriter,
                invoiceSubscriptionStateRepository
        );
    }

    @Test
    void processInvoiceUpdate_settledInvoiceDelegatesOutboxDetailsToWriter() {
        LightningInvoiceEntity invoiceEntity = LightningInvoiceEntity.builder()
                .id("invoice-123")
                .userId("user-123")
                .paymentRequest("lnbc1settled")
                .paymentHash("payment-hash")
                .invoiceState(LightningInvoiceEntity.InvoiceState.OPEN)
                .amountSats(2_500L)
                .amountPaidSats(0)
                .memo("invoice memo")
                .createdAt(LocalDateTime.now())
                .expiry(3600)
                .build();
        LightningInvoiceUpdate invoice = new LightningInvoiceUpdate(
                "lnbc1settled",
                LightningInvoiceUpdate.State.SETTLED,
                2_500L,
                10L,
                20L
        );
        when(lightningInvoiceRepository.findByPaymentRequest("lnbc1settled"))
                .thenReturn(Optional.of(invoiceEntity));
        when(lightningInvoiceRepository.save(invoiceEntity)).thenReturn(invoiceEntity);
        when(invoiceSubscriptionStateRepository.findById("singleton"))
                .thenReturn(Optional.of(InvoiceSubscriptionState.builder().id("singleton").build()));

        invoiceProcessorService.processInvoiceUpdate(invoice);

        ArgumentCaptor<InvoiceSettledEvent> eventCaptor = ArgumentCaptor.forClass(InvoiceSettledEvent.class);
        verify(outboxWriter).publishInvoiceSettled(eq("invoice-123"), eventCaptor.capture());
        InvoiceSettledEvent event = eventCaptor.getValue();
        assertEquals("user-123", event.getUserId());
        assertEquals(2_500L, event.getAmount());
        assertEquals("payment-hash", event.getPaymentHash());
        assertEquals("invoice memo", event.getMemo());
        verify(lightningInvoiceRepository).save(invoiceEntity);
        verify(invoiceSubscriptionStateRepository).save(argThat(state ->
                state.getAddIndex() == 10L && state.getSettleIndex() == 20L
        ));
    }
}
