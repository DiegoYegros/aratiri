package com.aratiri.infrastructure.messaging.listener;

import com.aratiri.infrastructure.messaging.outbox.OutboxWriter;
import com.aratiri.infrastructure.persistence.jpa.entity.AccountEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.InvoiceSubscriptionState;
import com.aratiri.infrastructure.persistence.jpa.entity.UserEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.AccountRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.InvoiceSubscriptionStateRepository;
import com.aratiri.transactions.application.event.OnChainTransactionReceivedEvent;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import lnrpc.LightningGrpc;
import lnrpc.OutputDetail;
import lnrpc.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OnChainTransactionListenerTest {

    @Mock
    private LightningGrpc.LightningStub lightningAsyncStub;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionsPort transactionsService;

    @Mock
    private OutboxWriter outboxWriter;

    @Mock
    private InvoiceSubscriptionStateRepository invoiceSubscriptionStateRepository;

    private OnChainTransactionListener listener;

    @BeforeEach
    void setUp() {
        listener = new OnChainTransactionListener(
                lightningAsyncStub,
                accountRepository,
                transactionsService,
                outboxWriter,
                invoiceSubscriptionStateRepository
        );
    }

    @Test
    void processTransaction_confirmedOwnedOutputDelegatesOutboxDetailsToWriter() {
        UserEntity user = new UserEntity();
        user.setId("user-123");
        AccountEntity account = new AccountEntity();
        account.setBitcoinAddress("bc1qowned");
        account.setUser(user);
        OutputDetail output = OutputDetail.newBuilder()
                .setIsOurAddress(true)
                .setAddress("bc1qowned")
                .setAmount(3_000L)
                .setOutputIndex(2L)
                .build();
        Transaction transaction = Transaction.newBuilder()
                .setTxHash("tx-hash")
                .setNumConfirmations(1)
                .setBlockHeight(250)
                .addOutputDetails(output)
                .build();
        when(accountRepository.findByBitcoinAddress("bc1qowned")).thenReturn(Optional.of(account));
        when(transactionsService.existsByReferenceId("tx-hash:2")).thenReturn(false);
        when(invoiceSubscriptionStateRepository.findById("singleton"))
                .thenReturn(Optional.of(InvoiceSubscriptionState.builder().id("singleton").lastTxBlockHeight(100L).build()));

        ReflectionTestUtils.invokeMethod(listener, "processTransaction", transaction);

        ArgumentCaptor<OnChainTransactionReceivedEvent> eventCaptor = ArgumentCaptor.forClass(OnChainTransactionReceivedEvent.class);
        verify(outboxWriter).publishOnChainTransactionReceived(eq("tx-hash:2"), eventCaptor.capture());
        OnChainTransactionReceivedEvent event = eventCaptor.getValue();
        assertEquals("user-123", event.getUserId());
        assertEquals(3_000L, event.getAmount());
        assertEquals("tx-hash", event.getTxHash());
        assertEquals(2L, event.getOutputIndex());
        verify(invoiceSubscriptionStateRepository).save(argThat(state -> state.getLastTxBlockHeight() == 250L));
    }
}
