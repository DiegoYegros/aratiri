package com.aratiri.payments.infrastructure.persistence;

import com.aratiri.infrastructure.persistence.jpa.entity.LightningInvoiceEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.LightningInvoiceRepository;
import com.aratiri.payments.domain.InternalLightningInvoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LightningInvoiceRepositoryAdapterTest {

  @Mock
  private LightningInvoiceRepository lightningInvoiceRepository;

  private LightningInvoiceRepositoryAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new LightningInvoiceRepositoryAdapter(lightningInvoiceRepository);
  }

  @Test
  void findByPaymentHash_pendingState_returnsPending() {
    LightningInvoiceEntity entity = LightningInvoiceEntity.builder()
        .id("inv-1")
        .userId("user-123")
        .paymentHash("hash123")
        .invoiceState(LightningInvoiceEntity.InvoiceState.OPEN)
        .build();

    when(lightningInvoiceRepository.findByPaymentHash("hash123")).thenReturn(Optional.of(entity));

    Optional<InternalLightningInvoice> result = adapter.findByPaymentHash("hash123");

    assertTrue(result.isPresent());
    assertEquals("user-123", result.get().userId());
    assertEquals(InternalLightningInvoice.InvoiceState.PENDING, result.get().state());
  }

  @Test
  void findByPaymentHash_settledState_returnsSettled() {
    LightningInvoiceEntity entity = LightningInvoiceEntity.builder()
        .id("inv-2")
        .userId("user-456")
        .paymentHash("hash456")
        .invoiceState(LightningInvoiceEntity.InvoiceState.SETTLED)
        .build();

    when(lightningInvoiceRepository.findByPaymentHash("hash456")).thenReturn(Optional.of(entity));

    Optional<InternalLightningInvoice> result = adapter.findByPaymentHash("hash456");

    assertTrue(result.isPresent());
    assertEquals("user-456", result.get().userId());
    assertEquals(InternalLightningInvoice.InvoiceState.SETTLED, result.get().state());
  }

  @Test
  void findByPaymentHash_acceptedState_returnsPending() {
    LightningInvoiceEntity entity = LightningInvoiceEntity.builder()
        .id("inv-3")
        .userId("user-789")
        .paymentHash("hash789")
        .invoiceState(LightningInvoiceEntity.InvoiceState.ACCEPTED)
        .build();

    when(lightningInvoiceRepository.findByPaymentHash("hash789")).thenReturn(Optional.of(entity));

    Optional<InternalLightningInvoice> result = adapter.findByPaymentHash("hash789");

    assertTrue(result.isPresent());
    assertEquals(InternalLightningInvoice.InvoiceState.PENDING, result.get().state());
  }

  @Test
  void findByPaymentHash_canceledState_returnsPending() {
    LightningInvoiceEntity entity = LightningInvoiceEntity.builder()
        .id("inv-4")
        .userId("user-000")
        .paymentHash("hash000")
        .invoiceState(LightningInvoiceEntity.InvoiceState.CANCELED)
        .build();

    when(lightningInvoiceRepository.findByPaymentHash("hash000")).thenReturn(Optional.of(entity));

    Optional<InternalLightningInvoice> result = adapter.findByPaymentHash("hash000");

    assertTrue(result.isPresent());
    assertEquals(InternalLightningInvoice.InvoiceState.PENDING, result.get().state());
  }

  @Test
  void findByPaymentHash_notFound_returnsEmpty() {
    when(lightningInvoiceRepository.findByPaymentHash("nonexistent")).thenReturn(Optional.empty());

    Optional<InternalLightningInvoice> result = adapter.findByPaymentHash("nonexistent");

    assertTrue(result.isEmpty());
  }
}
