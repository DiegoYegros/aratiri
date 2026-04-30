package com.aratiri.webhooks.application;

import com.aratiri.invoices.domain.LightningInvoice;
import com.aratiri.transactions.application.dto.TransactionStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class WebhookFactsTest {

    @Test
    void invoiceCreatedWebhookFacts_validatesNonNulls() {
        assertThrows(NullPointerException.class, () ->
                new InvoiceCreatedWebhookFacts(null, "u-1", "hash", "pr", 1000L, "memo", "ext", "meta"));
        assertThrows(NullPointerException.class, () ->
                new InvoiceCreatedWebhookFacts("inv-1", null, "hash", "pr", 1000L, "memo", "ext", "meta"));
        assertThrows(NullPointerException.class, () ->
                new InvoiceCreatedWebhookFacts("inv-1", "u-1", null, "pr", 1000L, "memo", "ext", "meta"));
    }

    @Test
    void invoiceCreatedWebhookFacts_fromInvoice_mapsAllFields() {
        LightningInvoice invoice = new LightningInvoice(
                "inv-1", "user-1", "payment-hash", "preimage", "payment-request",
                LightningInvoice.InvoiceState.OPEN, 5000L, LocalDateTime.now(),
                3600L, 0L, null, "test memo", "ext-ref", "metadata-value"
        );

        InvoiceCreatedWebhookFacts facts = InvoiceCreatedWebhookFacts.from(invoice);

        assertEquals("inv-1", facts.invoiceId());
        assertEquals("user-1", facts.userId());
        assertEquals("payment-hash", facts.paymentHash());
        assertEquals("payment-request", facts.paymentRequest());
        assertEquals(5000L, facts.amountSat());
        assertEquals("test memo", facts.memo());
        assertEquals("ext-ref", facts.externalReference());
        assertEquals("metadata-value", facts.metadata());
    }

    @Test
    void invoiceSettledWebhookFacts_validatesNonNulls() {
        assertThrows(NullPointerException.class, () ->
                new InvoiceSettledWebhookFacts(null, "u-1", "hash", 1000L, TransactionStatus.COMPLETED, "ref", "ext", "meta", 500L));
        assertThrows(NullPointerException.class, () ->
                new InvoiceSettledWebhookFacts("tx-1", null, "hash", 1000L, TransactionStatus.COMPLETED, "ref", "ext", "meta", 500L));
        assertThrows(NullPointerException.class, () ->
                new InvoiceSettledWebhookFacts("tx-1", "u-1", null, 1000L, TransactionStatus.COMPLETED, "ref", "ext", "meta", 500L));
        assertThrows(NullPointerException.class, () ->
                new InvoiceSettledWebhookFacts("tx-1", "u-1", "hash", 1000L, null, "ref", "ext", "meta", 500L));
    }

    @Test
    void invoiceSettledWebhookFacts_allFieldsAccessible() {
        InvoiceSettledWebhookFacts facts = new InvoiceSettledWebhookFacts(
                "tx-1", "u-1", "hash", 1000L, TransactionStatus.COMPLETED, "ref-1", "ext-ref-1", "meta", 5000L);

        assertEquals("tx-1", facts.transactionId());
        assertEquals("u-1", facts.userId());
        assertEquals("hash", facts.paymentHash());
        assertEquals(1000L, facts.amountSat());
        assertEquals(TransactionStatus.COMPLETED, facts.status());
        assertEquals("ref-1", facts.referenceId());
        assertEquals("ext-ref-1", facts.externalReference());
        assertEquals("meta", facts.metadata());
        assertEquals(5000L, facts.balanceAfterSat());
    }

    @Test
    void accountBalanceChangedFacts_validatesNonNulls() {
        assertThrows(NullPointerException.class, () ->
                new AccountBalanceChangedWebhookFacts(null, "u-1", "ext", "meta", 1000L, "ref", "ledger-1", 5000L));
        assertThrows(NullPointerException.class, () ->
                new AccountBalanceChangedWebhookFacts("tx-1", null, "ext", "meta", 1000L, "ref", "ledger-1", 5000L));
        assertThrows(NullPointerException.class, () ->
                new AccountBalanceChangedWebhookFacts("tx-1", "u-1", "ext", "meta", 1000L, "ref", null, 5000L));
    }

    @Test
    void accountBalanceChangedFacts_allFieldsAccessible() {
        AccountBalanceChangedWebhookFacts facts = new AccountBalanceChangedWebhookFacts(
                "tx-1", "u-1", "ext-ref", "meta", 1000L, "ref-1", "ledger-1", 5000L);

        assertEquals("tx-1", facts.transactionId());
        assertEquals("u-1", facts.userId());
        assertEquals("ext-ref", facts.externalReference());
        assertEquals("meta", facts.metadata());
        assertEquals(1000L, facts.amountSat());
        assertEquals("ref-1", facts.referenceId());
        assertEquals("ledger-1", facts.ledgerEntryId());
        assertEquals(5000L, facts.balanceAfterSat());
    }

    @Test
    void onChainDepositFacts_validatesNonNulls() {
        assertThrows(NullPointerException.class, () ->
                new OnChainDepositWebhookFacts(null, "u-1", 1000L, TransactionStatus.COMPLETED, "ref", "ext", "meta", 500L));
        assertThrows(NullPointerException.class, () ->
                new OnChainDepositWebhookFacts("tx-1", null, 1000L, TransactionStatus.COMPLETED, "ref", "ext", "meta", 500L));
        assertThrows(NullPointerException.class, () ->
                new OnChainDepositWebhookFacts("tx-1", "u-1", 1000L, null, "ref", "ext", "meta", 500L));
        assertThrows(NullPointerException.class, () ->
                new OnChainDepositWebhookFacts("tx-1", "u-1", 1000L, TransactionStatus.COMPLETED, null, "ext", "meta", 500L));
    }

    @Test
    void onChainDepositFacts_allFieldsAccessible() {
        OnChainDepositWebhookFacts facts = new OnChainDepositWebhookFacts(
                "tx-1", "u-1", 100000L, TransactionStatus.COMPLETED, "ref-1", "ext-ref", "onchain-meta", 1000000L);

        assertEquals("tx-1", facts.transactionId());
        assertEquals("u-1", facts.userId());
        assertEquals(100000L, facts.amountSat());
        assertEquals(TransactionStatus.COMPLETED, facts.status());
        assertEquals("ref-1", facts.referenceId());
        assertEquals("ext-ref", facts.externalReference());
        assertEquals("onchain-meta", facts.metadata());
        assertEquals(1000000L, facts.balanceAfterSat());
    }

    @Test
    void nodeOperationUnknownOutcomeFacts_allFieldsAccessible() {
        NodeOperationUnknownOutcomeFacts facts = new NodeOperationUnknownOutcomeFacts(
                "op-1", "tx-1", "u-1", "LIGHTNING_PAYMENT", "UNKNOWN_OUTCOME",
                "ref-1", "ext-id-1", 5, "timeout error", 1000L, "PENDING", "ext-ref", "meta");

        assertEquals("op-1", facts.operationId());
        assertEquals("tx-1", facts.transactionId());
        assertEquals("u-1", facts.userId());
        assertEquals("LIGHTNING_PAYMENT", facts.operationType());
        assertEquals("UNKNOWN_OUTCOME", facts.operationStatus());
        assertEquals("ref-1", facts.referenceId());
        assertEquals("ext-id-1", facts.externalId());
        assertEquals(5, facts.attemptCount());
        assertEquals("timeout error", facts.operationError());
        assertEquals(1000L, facts.amountSat());
        assertEquals("PENDING", facts.transactionStatus());
        assertEquals("ext-ref", facts.externalReference());
        assertEquals("meta", facts.metadata());
    }
}
