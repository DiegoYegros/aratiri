package com.aratiri.listener;

import com.aratiri.entity.OutboxEventEntity;
import com.aratiri.enums.KafkaTopics;
import com.aratiri.event.OnChainTransactionReceivedEvent;
import com.aratiri.repository.AccountRepository;
import com.aratiri.repository.OutboxEventRepository;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class OnChainTransactionListener {

    private static final Logger logger = LoggerFactory.getLogger(OnChainTransactionListener.class);

    private final LightningGrpc.LightningStub lightningAsyncStub;
    private final AccountRepository accountRepository;
    private final TransactionsPort transactionsService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean isListening = new AtomicBoolean(false);
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(true);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private StreamObserver<Transaction> transactionStreamObserver;

    public OnChainTransactionListener(
            LightningGrpc.LightningStub lightningAsyncStub,
            AccountRepository accountRepository,
            TransactionsPort transactionsService,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.lightningAsyncStub = lightningAsyncStub;
        this.accountRepository = accountRepository;
        this.transactionsService = transactionsService;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
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

    @Async
    public void subscribeToTransactions() {
        if (isListening.get()) {
            logger.debug("Already listening to on-chain transactions, skipping");
            return;
        }

        try {
            logger.info("Establishing on-chain transaction subscription stream");
            isListening.set(true);
            GetTransactionsRequest request = GetTransactionsRequest.newBuilder().build();

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
            if (!output.getIsOurAddress()) {
                logger.info("[{}] Is not our address. Skipping.", output.getAddress());
                continue;
            }
            logger.info("[{}] is our address. Processing.", output.getAddress());
            accountRepository.findByBitcoinAddress(output.getAddress()).ifPresent(account -> {
                if (transactionsService.existsByReferenceId(transaction.getTxHash())) {
                    logger.warn("Transaction already processed: {}", transaction.getTxHash());
                    return;
                }
                if (transaction.getNumConfirmations() > 0) {
                    OnChainTransactionReceivedEvent eventPayload = new OnChainTransactionReceivedEvent(
                            account.getUser().getId(),
                            output.getAmount(),
                            transaction.getTxHash()
                    );
                    try {
                        OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                                .aggregateType("ONCHAIN_TRANSACTION")
                                .aggregateId(transaction.getTxHash())
                                .eventType(KafkaTopics.ONCHAIN_TRANSACTION_RECEIVED.getCode())
                                .payload(objectMapper.writeValueAsString(eventPayload))
                                .build();
                        outboxEventRepository.save(outboxEvent);
                        logger.info("Saved ONCHAIN_TRANSACTION_RECEIVED event to outbox for txHash: {}", transaction.getTxHash());
                    } catch (Exception e) {
                        logger.error("Failed to create outbox event for on-chain transaction.", e);
                    }
                }
            });
        }
    }
}