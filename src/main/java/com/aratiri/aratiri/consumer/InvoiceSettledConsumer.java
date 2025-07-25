package com.aratiri.aratiri.consumer;

import com.aratiri.aratiri.constants.BitcoinConstants;
import com.aratiri.aratiri.dto.transactions.CreateTransactionRequest;
import com.aratiri.aratiri.enums.TransactionCurrency;
import com.aratiri.aratiri.enums.TransactionStatus;
import com.aratiri.aratiri.enums.TransactionType;
import com.aratiri.aratiri.event.InvoiceSettledEvent;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.service.TransactionsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceSettledConsumer {

    private final TransactionsService transactionsService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "invoice.settled", groupId = "invoice-listener-group")
    @RetryableTopic(
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
            include = {Exception.class, AratiriException.class}
    )
    public void handleInvoiceSettled(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        log.info("Received invoice settlement message from topic: {}, partition: {}, offset: {}",
                topic, partition, offset);
        try {
            InvoiceSettledEvent event = objectMapper.readValue(message, InvoiceSettledEvent.class);
            BigDecimal amountInSats = new BigDecimal(event.getAmount());
            BigDecimal amountInBTC = amountInSats.divide(BitcoinConstants.SATOSHIS_PER_BTC, 8, RoundingMode.HALF_UP);
            log.info("Processing invoice settlement for user: {}, amount: {}, paymentHash: {}, amountInBTC = {}",
                    event.getUserId(), event.getAmount(), event.getPaymentHash(), amountInBTC);
            boolean transactionExists = transactionsService.existsByReferenceId(event.getPaymentHash());
            if (transactionExists) {
                log.warn("Transaction with paymentHash {} already processed. Skipping.", event.getPaymentHash());
                acknowledgment.acknowledge();
                return;
            }
            CreateTransactionRequest request = new CreateTransactionRequest(
                    event.getUserId(),
                    amountInBTC,
                    TransactionCurrency.BTC,
                    TransactionType.INVOICE_CREDIT,
                    TransactionStatus.COMPLETED,
                    String.format("Payment received for invoice (hash: %s...)", event.getPaymentHash().substring(0, 10)),
                    event.getPaymentHash()
            );
            transactionsService.createAndSettleTransaction(request);
            log.info("Successfully processed invoice settlement for user: {}", event.getUserId());
            acknowledgment.acknowledge();
        } catch (JsonProcessingException e) {
            log.error("Couldn't deserialze invoice settlement message: {}", message, e);
            throw new AratiriException("Deserialization failed", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            log.error("Failed to process invoice settlement: {}", message, e);
            throw e;
        }
    }

    @DltHandler
    public void handleFailedInvoiceSettlement(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        log.error("Invoice settlement failed after all retries. Topic: {}, Message: {}, Error: {}",
                topic, message, exceptionMessage);
        try {
            InvoiceSettledEvent event = objectMapper.readValue(message, InvoiceSettledEvent.class);
            log.error("Failed invoice: userId={}, amount={}, paymentHash={}",
                    event.getUserId(), event.getAmount(), event.getPaymentHash());
        } catch (JsonProcessingException e) {
            log.error("Could not deserialize failed message for dead letter handling: {}", message, e);
        }
    }
}