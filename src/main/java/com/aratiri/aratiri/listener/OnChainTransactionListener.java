package com.aratiri.aratiri.listener;

import com.aratiri.aratiri.constants.BitcoinConstants;
import com.aratiri.aratiri.dto.transactions.CreateTransactionRequest;
import com.aratiri.aratiri.dto.transactions.TransactionCurrency;
import com.aratiri.aratiri.dto.transactions.TransactionStatus;
import com.aratiri.aratiri.dto.transactions.TransactionType;
import com.aratiri.aratiri.repository.AccountRepository;
import com.aratiri.aratiri.service.TransactionsService;
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
    private final TransactionsService transactionsService;
    private final AtomicBoolean isListening = new AtomicBoolean(false);
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(true);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private StreamObserver<Transaction> transactionStreamObserver;

    public OnChainTransactionListener(LightningGrpc.LightningStub lightningAsyncStub, AccountRepository accountRepository, TransactionsService transactionsService) {
        this.lightningAsyncStub = lightningAsyncStub;
        this.accountRepository = accountRepository;
        this.transactionsService = transactionsService;
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
            accountRepository.findByBitcoinAddress(output.getAddress()).ifPresent(account -> {
                if (transactionsService.existsByReferenceId(transaction.getTxHash())) {
                    logger.warn("Transaction already processed: {}", transaction.getTxHash());
                    return;
                }
                if (transaction.getNumConfirmations() > 0) {
                    CreateTransactionRequest creditRequest = new CreateTransactionRequest(
                            account.getUser().getId(),
                            BitcoinConstants.satoshisToBtc(output.getAmount()),
                            TransactionCurrency.BTC,
                            TransactionType.ONCHAIN_CREDIT,
                            TransactionStatus.COMPLETED,
                            "On-chain payment received",
                            transaction.getTxHash()
                    );
                    transactionsService.createAndSettleTransaction(creditRequest);
                    logger.info("Credited account {} with {} sats.", account.getId(), output.getAmount());
                }
            });
        }
    }
}