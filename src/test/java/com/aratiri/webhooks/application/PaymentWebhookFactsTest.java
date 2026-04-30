package com.aratiri.webhooks.application;

import com.aratiri.transactions.application.dto.TransactionStatus;
import com.aratiri.transactions.application.dto.TransactionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentWebhookFactsTest {

    @Test
    void constructor_validatesNonNullFields() {
        assertThrows(NullPointerException.class, () ->
                new PaymentWebhookFacts(null, "u-1", TransactionType.LIGHTNING_DEBIT, 1000L, TransactionStatus.PENDING, "ref", "ext", "meta", 500L, null));
        assertThrows(NullPointerException.class, () ->
                new PaymentWebhookFacts("tx-1", null, TransactionType.LIGHTNING_DEBIT, 1000L, TransactionStatus.PENDING, "ref", "ext", "meta", 500L, null));
        assertThrows(NullPointerException.class, () ->
                new PaymentWebhookFacts("tx-1", "u-1", null, 1000L, TransactionStatus.PENDING, "ref", "ext", "meta", 500L, null));
        assertThrows(NullPointerException.class, () ->
                new PaymentWebhookFacts("tx-1", "u-1", TransactionType.LIGHTNING_DEBIT, 1000L, null, "ref", "ext", "meta", 500L, null));
    }

    @Test
    void isDebitPayment_debitTypes() {
        PaymentWebhookFacts facts = new PaymentWebhookFacts(
                "tx-1", "u-1", TransactionType.LIGHTNING_DEBIT, 1000L, TransactionStatus.PENDING, null, null, null, null, null);
        assertTrue(facts.isDebitPayment());
    }

    @Test
    void isDebitPayment_creditTypes() {
        PaymentWebhookFacts facts = new PaymentWebhookFacts(
                "tx-1", "u-1", TransactionType.LIGHTNING_CREDIT, 1000L, TransactionStatus.PENDING, null, null, null, null, null);
        assertFalse(facts.isDebitPayment());
    }

    @Test
    void accepted_factorySetsPendingStatus() {
        PaymentWebhookFacts facts = PaymentWebhookFacts.accepted(
                "tx-1", "u-1", TransactionType.LIGHTNING_DEBIT, 1000L, "ref-1", "ext-1", "test meta");

        assertEquals(TransactionStatus.PENDING, facts.status());
        assertNull(facts.balanceAfterSat());
        assertNull(facts.failureReason());
        assertEquals("tx-1", facts.transactionId());
    }

    @Test
    void accessorsWork() {
        PaymentWebhookFacts facts = new PaymentWebhookFacts(
                "tx-abc", "user-xyz", TransactionType.ONCHAIN_DEBIT, 50000L,
                TransactionStatus.FAILED, "ref", "ext-ref", "metadata-value", 100000L, "insufficient funds");

        assertEquals("tx-abc", facts.transactionId());
        assertEquals("user-xyz", facts.userId());
        assertEquals(TransactionType.ONCHAIN_DEBIT, facts.type());
        assertEquals(50000L, facts.amountSat());
        assertEquals(TransactionStatus.FAILED, facts.status());
        assertEquals("ref", facts.referenceId());
        assertEquals("ext-ref", facts.externalReference());
        assertEquals("metadata-value", facts.metadata());
        assertEquals(100000L, facts.balanceAfterSat());
        assertEquals("insufficient funds", facts.failureReason());
        assertTrue(facts.isDebitPayment());
    }
}
