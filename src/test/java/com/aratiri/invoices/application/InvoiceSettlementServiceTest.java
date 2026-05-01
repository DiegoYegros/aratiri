package com.aratiri.invoices.application;

import com.aratiri.invoices.application.port.out.LightningInvoicePersistencePort;
import com.aratiri.invoices.domain.LightningInvoice;
import com.aratiri.shared.exception.AratiriException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceSettlementServiceTest {

    @Mock
    private LightningInvoicePersistencePort lightningInvoicePersistencePort;

    private InvoiceSettlementService service;

    @BeforeEach
    void setUp() {
        service = new InvoiceSettlementService(lightningInvoicePersistencePort);
    }

    @Test
    void settlementFacts_foundInvoice_returnsMemoExternalReferenceAndMetadata() {
        LightningInvoice invoice = invoice(
                "user-1",
                "payment-hash-1234567890",
                LightningInvoice.InvoiceState.OPEN,
                2_100L,
                0,
                null,
                "invoice memo"
        );
        when(lightningInvoicePersistencePort.findByPaymentHash("payment-hash-1234567890"))
                .thenReturn(Optional.of(invoice));

        InvoiceSettlementFacts facts = service.settlementFacts("payment-hash-1234567890");

        assertEquals("payment-hash-1234567890", facts.paymentHash());
        assertEquals("invoice memo", facts.memo());
        assertEquals("invoice memo", facts.description());
        assertEquals("external-reference", facts.externalReference());
        assertEquals("{\"order_id\":\"1001\"}", facts.metadata());
    }

    @Test
    void settlementFacts_missingInvoice_returnsFallbackDescription() {
        when(lightningInvoicePersistencePort.findByPaymentHash("payment-hash-1234567890"))
                .thenReturn(Optional.empty());

        InvoiceSettlementFacts facts = service.settlementFacts("payment-hash-1234567890");

        assertEquals("payment-hash-1234567890", facts.paymentHash());
        assertNull(facts.memo());
        assertEquals("Payment received for invoice (hash: payment-ha...)", facts.description());
        assertNull(facts.externalReference());
        assertNull(facts.metadata());
    }

    @Test
    void settleInternalInvoice_openInvoiceMarksInvoiceSettled() {
        LightningInvoice openInvoice = invoice(
                "receiver-1",
                "payment-hash",
                LightningInvoice.InvoiceState.OPEN,
                1_000L,
                0,
                null,
                "internal memo"
        );
        when(lightningInvoicePersistencePort.findByPaymentHash("payment-hash"))
                .thenReturn(Optional.of(openInvoice));
        when(lightningInvoicePersistencePort.save(any(LightningInvoice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InternalInvoiceSettlementFacts facts = service.settleInternalInvoice(
                new SettleInternalInvoiceCommand("receiver-1", "payment-hash", 1_000L)
        );

        ArgumentCaptor<LightningInvoice> invoiceCaptor = ArgumentCaptor.forClass(LightningInvoice.class);
        verify(lightningInvoicePersistencePort).save(invoiceCaptor.capture());
        LightningInvoice savedInvoice = invoiceCaptor.getValue();
        assertEquals(LightningInvoice.InvoiceState.SETTLED, savedInvoice.invoiceState());
        assertEquals(1_000L, savedInvoice.amountPaidSats());
        assertNotNull(savedInvoice.settledAt());
        assertEquals("payment-hash", facts.paymentHash());
        assertEquals("receiver-1", facts.receiverId());
        assertEquals(1_000L, facts.amountPaidSats());
        assertEquals("internal memo", facts.memo());
    }

    @Test
    void settleInternalInvoice_alreadySettledWithDifferentAmountThrows() {
        LightningInvoice settledInvoice = invoice(
                "receiver-1",
                "payment-hash",
                LightningInvoice.InvoiceState.SETTLED,
                1_000L,
                900L,
                LocalDateTime.now(),
                "internal memo"
        );
        when(lightningInvoicePersistencePort.findByPaymentHash("payment-hash"))
                .thenReturn(Optional.of(settledInvoice));
        SettleInternalInvoiceCommand command = new SettleInternalInvoiceCommand("receiver-1", "payment-hash", 1_000L);

        assertThrows(AratiriException.class, () -> service.settleInternalInvoice(command));
        verify(lightningInvoicePersistencePort, never()).save(any());
    }

    @Test
    void recordInvoiceStateUpdate_settledInvoiceReturnsPublication() {
        LightningInvoice openInvoice = invoice(
                "user-1",
                "payment-hash",
                LightningInvoice.InvoiceState.OPEN,
                2_500L,
                0,
                null,
                "invoice memo"
        );
        when(lightningInvoicePersistencePort.findByPaymentRequest("lnbc1settled"))
                .thenReturn(Optional.of(openInvoice));
        when(lightningInvoicePersistencePort.save(any(LightningInvoice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InvoiceStateUpdateResult result = service.recordInvoiceStateUpdate(new InvoiceStateUpdate(
                "lnbc1settled",
                InvoiceStateUpdate.State.SETTLED,
                2_500L
        ));

        assertTrue(result.stateChanged());
        assertTrue(result.settledPublication().isPresent());
        InvoiceSettledPublication publication = result.settledPublication().get();
        assertEquals("invoice-1", publication.invoiceId());
        assertEquals("user-1", publication.event().getUserId());
        assertEquals(2_500L, publication.event().getAmount());
        assertEquals("payment-hash", publication.event().getPaymentHash());
        assertEquals("invoice memo", publication.event().getMemo());
    }

    private LightningInvoice invoice(
            String userId,
            String paymentHash,
            LightningInvoice.InvoiceState state,
            long amountSats,
            long amountPaidSats,
            LocalDateTime settledAt,
            String memo
    ) {
        return new LightningInvoice(
                "invoice-1",
                userId,
                paymentHash,
                "preimage",
                "payment-request",
                state,
                amountSats,
                LocalDateTime.now(),
                3600,
                amountPaidSats,
                settledAt,
                memo,
                "external-reference",
                "{\"order_id\":\"1001\"}"
        );
    }
}
