package com.aratiri.infrastructure.messaging.listener;

import com.aratiri.infrastructure.messaging.KafkaTopics;
import com.aratiri.infrastructure.persistence.jpa.entity.InvoiceSubscriptionState;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.AccountRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.InvoiceSubscriptionStateRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.OutboxEventRepository;
import com.aratiri.transactions.application.event.OnChainTransactionReceivedEvent;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lnrpc.GetTransactionsRequest;
import lnrpc.LightningGrpc;
import lnrpc.OutputDetail;
import lnrpc.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class OnChainTransactionListener {

    private static final Logger logger = LoggerFactory.getLogger(OnChainTransactionListener.class);
    private static final String SUBSCRIPTION_STATE_ID = "singleton";

    private final LightningGrpc.LightningStub lightningAsyncStub;
    private final AccountRepository accountRepository;
    private final TransactionsPort transactionsService;
    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;
    private final AtomicBoolean isListening = new AtomicBoolean(false);
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(true);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private StreamObserver<Transaction> transactionStreamObserver;
    private final InvoiceSubscriptionStateRepository invoiceSubscriptionStateRepository;

    public OnChainTransactionListener(
            LightningGrpc.LightningStub lightningAsyncStub,
            AccountRepository accountRepository,
            TransactionsPort transactionsService,
            OutboxEventRepository outboxEventRepository,
            JsonMapper objectMapper, InvoiceSubscriptionStateRepository invoiceSubscriptionStateRepository) {
        this.lightningAsyncStub = lightningAsyncStub;
        this.accountRepository = accountRepository;
        this.transactionsService = transactionsService;
        this.outboxEventRepository = outboxEventRepository;
        this.jsonMapper = objectMapper;
        this.invoiceSubscriptionStateRepository = invoiceSubscriptionStateRepository;
    }

    @PostConstruct
    public void startListening() {
        logger.info("Scheduling On-Chain Transaction Listener startup...");
        shouldReconnect.set(true);
    }

    @PreDestroy
    public void stopListening() {
        logger.info("Stopping On-Chain Transaction Listener...");
        isListening.set(false);
        shouldReconnect.set(false);

        if (transactionStreamObserver != null) {
            try {
                transactionStreamObserver.onCompleted();
            } catch (Exception e) {
                logger.debug("Error completing stream observer during shutdown: {}", e.getMessage());
            }
        }

        shutdownLatch.countDown();
    }

    @EventListener
    public void handleContextClosedEvent(ContextClosedEvent event) {
        logger.info("Application context closing, stopping on-chain transaction listener");
        stopListening();
    }

    public void subscribeToTransactions() {
        if (isListening.get()) {
            logger.debug("Already listening to on-chain transactions, skipping");
            return;
        }

        try {
            logger.info("Establishing on-chain transaction subscription stream");
            isListening.set(true);
            InvoiceSubscriptionState state = invoiceSubscriptionStateRepository.findById(SUBSCRIPTION_STATE_ID)
                    .orElse(InvoiceSubscriptionState.builder().id(SUBSCRIPTION_STATE_ID).build());
            logger.info("Subscribing to transactions from block height [{}]", state.getLastTxBlockHeight());
            GetTransactionsRequest request = GetTransactionsRequest.newBuilder()
                    .setStartHeight((int) state.getLastTxBlockHeight())
                    .build();
            transactionStreamObserver = new StreamObserver<>() {
                @Override
                public void onNext(Transaction transaction) {
                    processTransaction(transaction);
                }

                @Override
                public void onError(Throwable t) {
                    logger.error("Error in transaction subscription stream", t);
                    isListening.set(false);
                    if (shutdownLatch.getCount() > 0) {
                        shouldReconnect.set(true);
                    }
                }

                @Override
                public void onCompleted() {
                    logger.info("Transaction subscription stream completed");
                    isListening.set(false);
                    if (shutdownLatch.getCount() > 0) {
                        shouldReconnect.set(true);
                    }
                }
            };

            lightningAsyncStub.subscribeTransactions(request, transactionStreamObserver);
            logger.info("Successfully subscribed to on-chain transaction updates");

        } catch (Exception e) {
            logger.error("Failed to establish on-chain transaction subscription", e);
            isListening.set(false);
            if (shutdownLatch.getCount() > 0) {
                shouldReconnect.set(true);
            }
        }
    }

    @Scheduled(fixedDelay = 10000)
    public void checkReconnection() {
        if (shouldReconnect.get() && !isListening.get() && shutdownLatch.getCount() > 0) {
            logger.info("Attempting to reconnect to on-chain transaction stream");
            shouldReconnect.set(false);
            subscribeToTransactions();
        }
    }

    private void processTransaction(Transaction transaction) {
        logger.info("Received on-chain transaction: {}", transaction.getTxHash());

        for (OutputDetail output : transaction.getOutputDetailsList()) {
            processOutput(transaction, output);
        }
    }

    private void processOutput(Transaction transaction, OutputDetail output) {
        if (!output.getIsOurAddress()) {
            logger.info("[{}] Is not our address. Skipping.", output.getAddress());
            return;
        }

        logger.info("[{}] is our address. Processing.", output.getAddress());
        accountRepository.findByBitcoinAddress(output.getAddress())
                .ifPresent(account -> processOwnedOutput(transaction, output, account.getUser().getId()));
    }

    private void processOwnedOutput(Transaction transaction, OutputDetail output, String userId) {
        String referenceId = transaction.getTxHash() + ":" + output.getOutputIndex();
        if (transactionsService.existsByReferenceId(referenceId)) {
            logger.warn("Transaction already processed: {}", referenceId);
            return;
        }
        if (transaction.getNumConfirmations() <= 0) {
            return;
        }

        OnChainTransactionReceivedEvent eventPayload = new OnChainTransactionReceivedEvent(
                userId,
                output.getAmount(),
                transaction.getTxHash(),
                output.getOutputIndex()
        );
        try {
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .aggregateType("ONCHAIN_TRANSACTION")
                    .aggregateId(referenceId)
                    .eventType(KafkaTopics.ONCHAIN_TRANSACTION_RECEIVED.getCode())
                    .payload(jsonMapper.writeValueAsString(eventPayload))
                    .build();
            outboxEventRepository.save(outboxEvent);
            updateLastProcessedBlockHeight(transaction.getBlockHeight());
            logger.info("Saved ONCHAIN_TRANSACTION_RECEIVED event to outbox for txHash: {}", transaction.getTxHash());
        } catch (Exception e) {
            logger.error("Failed to create outbox event for on-chain transaction.", e);
        }
    }

    private void updateLastProcessedBlockHeight(long blockHeight) {
        InvoiceSubscriptionState state = invoiceSubscriptionStateRepository.findById(SUBSCRIPTION_STATE_ID)
                .orElse(InvoiceSubscriptionState.builder().id(SUBSCRIPTION_STATE_ID).build());
        if (blockHeight > state.getLastTxBlockHeight()) {
            state.setLastTxBlockHeight(blockHeight);
            invoiceSubscriptionStateRepository.save(state);
        }
    }
}
