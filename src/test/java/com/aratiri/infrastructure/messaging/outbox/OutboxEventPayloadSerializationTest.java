package com.aratiri.infrastructure.messaging.outbox;

import com.aratiri.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.OutboxEventRepository;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.event.PaymentInitiatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxEventPayloadSerializationTest {

  @Mock
  private OutboxEventRepository outboxEventRepository;

  private final JsonMapper jsonMapper = new JsonMapper();

  @Test
  void paymentInitiatedPayload_roundTripsThroughOutboxJson() throws Exception {
    PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
    request.setInvoice("lnbc1paymentrequest");
    request.setFeeLimitSat(25L);
    request.setTimeoutSeconds(30);
    request.setExternalReference("external-ref-123");
    request.setMetadata("{\"orderId\":\"order-123\"}");

    PaymentInitiatedEvent event = new PaymentInitiatedEvent("user-123", "tx-123", request);
    request.setInvoice("mutated-after-event-creation");

    new OutboxWriterService(outboxEventRepository, jsonMapper)
      .publishPaymentInitiated("tx-123", event);

    PaymentInitiatedEvent roundTripped = jsonMapper.readValue(savedEvent().getPayload(), PaymentInitiatedEvent.class);
    PayInvoiceRequestDTO roundTrippedRequest = roundTripped.getPayRequest();

    assertEquals("user-123", roundTripped.userId());
    assertEquals("tx-123", roundTripped.transactionId());
    assertEquals("lnbc1paymentrequest", roundTrippedRequest.getInvoice());
    assertEquals(25L, roundTrippedRequest.getFeeLimitSat());
    assertEquals(30, roundTrippedRequest.getTimeoutSeconds());
    assertEquals("external-ref-123", roundTrippedRequest.getExternalReference());
    assertEquals("{\"orderId\":\"order-123\"}", roundTrippedRequest.getMetadata());
  }

  private OutboxEventEntity savedEvent() {
    ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
    verify(outboxEventRepository).save(captor.capture());
    return captor.getValue();
  }
}
