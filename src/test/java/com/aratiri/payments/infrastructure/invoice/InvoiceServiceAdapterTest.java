package com.aratiri.payments.infrastructure.invoice;

import com.aratiri.invoices.application.dto.DecodedInvoicetDTO;
import com.aratiri.invoices.application.port.in.InvoicesPort;
import com.aratiri.payments.domain.DecodedInvoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceAdapterTest {

  @Mock
  private InvoicesPort invoicesPort;

  private InvoiceServiceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new InvoiceServiceAdapter(invoicesPort);
  }

  @Test
  void decodeInvoice_shouldMapCorrectly() {
    DecodedInvoicetDTO dto = DecodedInvoicetDTO.builder()
        .paymentHash("hash123")
        .numSatoshis(5000L)
        .description("Test invoice")
        .build();

    when(invoicesPort.decodePaymentRequest("lnbc1test")).thenReturn(dto);

    DecodedInvoice result = adapter.decodeInvoice("lnbc1test");

    assertEquals("hash123", result.paymentHash());
    assertEquals(5000L, result.amountSatoshis());
    assertEquals("Test invoice", result.description());
    verify(invoicesPort).decodePaymentRequest("lnbc1test");
  }

  @Test
  void existsSettledInvoice_true_delegatesCorrectly() {
    when(invoicesPort.existsSettledInvoiceByPaymentHash("hash123")).thenReturn(true);

    boolean result = adapter.existsSettledInvoice("hash123");

    assertTrue(result);
    verify(invoicesPort).existsSettledInvoiceByPaymentHash("hash123");
  }

  @Test
  void existsSettledInvoice_false_delegatesCorrectly() {
    when(invoicesPort.existsSettledInvoiceByPaymentHash("hash456")).thenReturn(false);

    boolean result = adapter.existsSettledInvoice("hash456");

    assertFalse(result);
    verify(invoicesPort).existsSettledInvoiceByPaymentHash("hash456");
  }
}
