package com.aratiri.infrastructure.scheduling.job;

import com.aratiri.admin.application.port.out.NodeSettingsPort;
import com.aratiri.admin.domain.NodeSettings;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.TransactionsRepository;
import com.aratiri.payments.application.port.in.PaymentsPort;
import com.aratiri.payments.domain.LightningPayment;
import com.aratiri.payments.domain.LightningPaymentStatus;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionReconciliationJobTest {

    @Mock
    private TransactionsRepository transactionsRepository;

    @Mock
    private NodeSettingsPort nodeSettingsPort;

    @Mock
    private PaymentsPort paymentsPort;

    @Mock
    private TransactionsPort transactionsService;

    private TransactionReconciliationJob job;

    @BeforeEach
    void setUp() {
        job = new TransactionReconciliationJob(
                transactionsRepository, nodeSettingsPort, paymentsPort, transactionsService);
    }

    @Test
    void reconcilePendingPayments_noPendingTransactions() {
        when(nodeSettingsPort.loadSettings())
                .thenReturn(new NodeSettings(true, 60000L, Instant.now(), Instant.now()));
        when(transactionsRepository.findPendingTransactionsOlderThan(any()))
                .thenReturn(List.of());

        job.reconcilePendingPayments();

        verify(paymentsPort, never()).checkPaymentStatusOnNode(anyString());
    }

    @Test
    void reconcilePendingPayments_skipsWhenNoPaymentHash() {
        TransactionEntity tx = new TransactionEntity();
        tx.setId(java.util.UUID.randomUUID().toString());
        tx.setUserId("user-1");

        when(nodeSettingsPort.loadSettings())
                .thenReturn(new NodeSettings(true, 60000L, Instant.now(), Instant.now()));
        when(transactionsRepository.findPendingTransactionsOlderThan(any()))
                .thenReturn(List.of(tx));

        job.reconcilePendingPayments();

        verify(paymentsPort, never()).checkPaymentStatusOnNode(anyString());
    }

    @Test
    void reconcilePendingPayments_confirmsSucceededPayment() {
        TransactionEntity tx = new TransactionEntity();
        tx.setId(java.util.UUID.randomUUID().toString());
        tx.setUserId("user-1");
        tx.setReferenceId("deadbeef");

        LightningPayment payment = lightningPayment(LightningPaymentStatus.SUCCEEDED);

        when(nodeSettingsPort.loadSettings())
                .thenReturn(new NodeSettings(true, 60000L, Instant.now(), Instant.now()));
        when(transactionsRepository.findPendingTransactionsOlderThan(any()))
                .thenReturn(List.of(tx));
        when(paymentsPort.checkPaymentStatusOnNode("deadbeef"))
                .thenReturn(Optional.of(payment));

        job.reconcilePendingPayments();

        verify(transactionsService).confirmTransaction(tx.getId(), tx.getUserId());
    }

    @Test
    void reconcilePendingPayments_failsFailedPayment() {
        TransactionEntity tx = new TransactionEntity();
        tx.setId(java.util.UUID.randomUUID().toString());
        tx.setUserId("user-1");
        tx.setReferenceId("deadbeef");

        LightningPayment payment = lightningPayment(LightningPaymentStatus.FAILED);

        when(nodeSettingsPort.loadSettings())
                .thenReturn(new NodeSettings(true, 60000L, Instant.now(), Instant.now()));
        when(transactionsRepository.findPendingTransactionsOlderThan(any()))
                .thenReturn(List.of(tx));
        when(paymentsPort.checkPaymentStatusOnNode("deadbeef"))
                .thenReturn(Optional.of(payment));

        job.reconcilePendingPayments();

        verify(transactionsService).failTransaction(eq(tx.getId()), anyString());
    }

    @Test
    void reconcilePendingPayments_failsWhenPaymentNotFound() {
        TransactionEntity tx = new TransactionEntity();
        tx.setId(java.util.UUID.randomUUID().toString());
        tx.setUserId("user-1");
        tx.setReferenceId("deadbeef");

        when(nodeSettingsPort.loadSettings())
                .thenReturn(new NodeSettings(true, 60000L, Instant.now(), Instant.now()));
        when(transactionsRepository.findPendingTransactionsOlderThan(any()))
                .thenReturn(List.of(tx));
        when(paymentsPort.checkPaymentStatusOnNode("deadbeef"))
                .thenReturn(Optional.empty());

        job.reconcilePendingPayments();

        verify(transactionsService).failTransaction(eq(tx.getId()), anyString());
    }

    @Test
    void reconcilePendingPayments_reconcileHandlesExceptionPerTransaction() {
        TransactionEntity tx1 = new TransactionEntity();
        tx1.setId("tx-1");
        tx1.setUserId("user-1");
        tx1.setReferenceId("hash1");
        TransactionEntity tx2 = new TransactionEntity();
        tx2.setId("tx-2");
        tx2.setUserId("user-1");
        tx2.setReferenceId("hash2");

        when(nodeSettingsPort.loadSettings())
                .thenReturn(new NodeSettings(true, 60000L, Instant.now(), Instant.now()));
        when(transactionsRepository.findPendingTransactionsOlderThan(any()))
                .thenReturn(List.of(tx1, tx2));
        when(paymentsPort.checkPaymentStatusOnNode("hash1"))
                .thenThrow(new RuntimeException("node error"));
        when(paymentsPort.checkPaymentStatusOnNode("hash2"))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> job.reconcilePendingPayments());

        verify(transactionsService).failTransaction(eq("tx-2"), anyString());
    }

    private LightningPayment lightningPayment(LightningPaymentStatus status) {
        return new LightningPayment(status, "FAILURE_REASON_NONE", 0, 0);
    }
}
