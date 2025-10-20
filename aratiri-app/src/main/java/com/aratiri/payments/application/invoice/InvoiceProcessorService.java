package com.aratiri.payments.application.invoice;

import com.aratiri.infrastructure.persistence.jpa.entity.InvoiceSubscriptionState;
import com.aratiri.infrastructure.persistence.jpa.entity.LightningInvoiceEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.aratiri.infrastructure.messaging.KafkaTopics;
import com.aratiri.invoices.application.event.InvoiceSettledEvent;
import com.aratiri.infrastructure.persistence.jpa.repository.InvoiceSubscriptionStateRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.LightningInvoiceRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.OutboxEventRepository;
import com.aratiri.auth.application.port.out.NotificationPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import lnrpc.Invoice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class InvoiceProcessorService {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceProcessorService.class);

    private final LightningInvoiceRepository lightningInvoiceRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final InvoiceSubscriptionStateRepository invoiceSubscriptionStateRepository;
    private final NotificationPort notificationsService;

    public InvoiceProcessorService(LightningInvoiceRepository lightningInvoiceRepository,
                                   OutboxEventRepository outboxEventRepository,
                                   ObjectMapper objectMapper, InvoiceSubscriptionStateRepository invoiceSubscriptionStateRepository, NotificationPort notificationsService) {
        this.lightningInvoiceRepository = lightningInvoiceRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.invoiceSubscriptionStateRepository = invoiceSubscriptionStateRepository;
        this.notificationsService = notificationsService;
    }

    @Transactional
    @Async
    public void processInvoiceUpdate(Invoice invoice) throws Exception {
        if (invoice.getState().equals(Invoice.InvoiceState.OPEN)) {
            return;
        }

        Optional<LightningInvoiceEntity> optionalInvoiceEntity =
                lightningInvoiceRepository.findByPaymentRequest(invoice.getPaymentRequest());

        if (optionalInvoiceEntity.isEmpty()) {
            logger.debug("Invoice not found in database, skipping: {}", invoice.getPaymentRequest());
            return;
        }

        LightningInvoiceEntity invoiceEntity = optionalInvoiceEntity.get();
        LightningInvoiceEntity.InvoiceState newState = mapInvoiceState(invoice.getState());

        if (invoiceEntity.getInvoiceState().equals(newState)) {
            return;
        }

        logger.info("Invoice state changed from {} to {} for payment request: {}",
                invoiceEntity.getInvoiceState(), newState, invoice.getPaymentRequest());

        invoiceEntity.setInvoiceState(newState);
        if (newState == LightningInvoiceEntity.InvoiceState.SETTLED) {
            invoiceEntity.setSettledAt(LocalDateTime.now());
            invoiceEntity.setAmountPaidSats(invoice.getAmtPaidSat());
            logger.info("Invoice settled for {} sats: {}", invoice.getAmtPaidSat(), invoice.getPaymentRequest());

            lightningInvoiceRepository.save(invoiceEntity);

            InvoiceSettledEvent eventPayload = new InvoiceSettledEvent(
                    invoiceEntity.getUserId(),
                    invoiceEntity.getAmountSats(),
                    invoiceEntity.getPaymentHash(),
                    LocalDateTime.now(),
                    invoiceEntity.getMemo()
            );

            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .aggregateType("Invoice")
                    .aggregateId(invoiceEntity.getId())
                    .eventType(KafkaTopics.INVOICE_SETTLED.getCode())
                    .payload(objectMapper.writeValueAsString(eventPayload))
                    .build();

            outboxEventRepository.save(outboxEvent);
            String userId = invoiceEntity.getUserId();
            Map<String, Object> notificationPayload = Map.of(
                    "message", "Payment Received",
                    "amountSats", invoice.getAmtPaidSat(),
                    "memo", invoice.getMemo()
            );
            notificationsService.sendNotification(userId, "payment_received", notificationPayload);
            logger.info("Saved INVOICE_SETTLED event to outbox for invoiceId: {}", invoiceEntity.getId());
        } else {
            lightningInvoiceRepository.save(invoiceEntity);
        }
        InvoiceSubscriptionState state = invoiceSubscriptionStateRepository.findById("singleton").orElse(InvoiceSubscriptionState.builder().id("singleton").build());
        state.setAddIndex(invoice.getAddIndex());
        state.setSettleIndex(invoice.getSettleIndex());
        invoiceSubscriptionStateRepository.save(state);
    }

    private LightningInvoiceEntity.InvoiceState mapInvoiceState(Invoice.InvoiceState grpcState) {
        return switch (grpcState) {
            case OPEN -> LightningInvoiceEntity.InvoiceState.OPEN;
            case SETTLED -> LightningInvoiceEntity.InvoiceState.SETTLED;
            case CANCELED -> LightningInvoiceEntity.InvoiceState.CANCELED;
            case ACCEPTED -> LightningInvoiceEntity.InvoiceState.ACCEPTED;
            default -> {
                logger.warn("Unknown invoice state: {}, defaulting to OPEN", grpcState);
                yield LightningInvoiceEntity.InvoiceState.OPEN;
            }
        };
    }
}