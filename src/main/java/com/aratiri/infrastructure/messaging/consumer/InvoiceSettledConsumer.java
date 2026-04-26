package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.infrastructure.messaging.KafkaTopicNames;
import com.aratiri.infrastructure.persistence.jpa.entity.LightningInvoiceEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.LightningInvoiceRepository;
import com.aratiri.invoices.application.event.InvoiceSettledEvent;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.InvoiceCreditSettlement;
import com.aratiri.transactions.application.TransactionSettlementModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceSettledConsumer {

    private final TransactionSettlementModule transactionSettlementModule;
    private final LightningInvoiceRepository lightningInvoiceRepository;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = KafkaTopicNames.INVOICE_SETTLED, groupId = "invoice-listener-group")
    @RetryableTopic(
            backOff = @BackOff(delay = 1000, multiplier = 2.0),
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
            InvoiceSettledEvent event = jsonMapper.readValue(message, InvoiceSettledEvent.class);
            long amountInSats = event.getAmount();
            log.info("Processing invoice settlement for user: {}, amount: {} sats, paymentRequest: {}",
                    event.getUserId(), amountInSats, event.getPaymentHash());
            LightningInvoiceEntity invoice = lightningInvoiceRepository.findByPaymentHash(event.getPaymentHash())
                    .orElse(null);
            String description = invoice != null && invoice.getMemo() != null
                    ? invoice.getMemo()
                    : String.format("Payment received for invoice (hash: %s...)", event.getPaymentHash().substring(0, 10));
            String externalReference = invoice != null ? invoice.getExternalReference() : null;
            String metadata = invoice != null ? invoice.getMetadata() : null;

            InvoiceCreditSettlement settlement = new InvoiceCreditSettlement(
                    event.getUserId(),
                    amountInSats,
                    event.getPaymentHash(),
                    description,
                    externalReference,
                    metadata
            );
            transactionSettlementModule.settleInvoiceCredit(settlement);
            log.info("Successfully processed invoice settlement for user: {}", event.getUserId());
            acknowledgment.acknowledge();
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
            InvoiceSettledEvent event = jsonMapper.readValue(message, InvoiceSettledEvent.class);
            log.error("Failed invoice: userId={}, amount={}, paymentRequest={}",
                    event.getUserId(), event.getAmount(), event.getPaymentHash());
        } catch (Exception e) {
            log.error("Could not deserialize failed message for dead letter handling: {}", message, e);
        }
    }
}
