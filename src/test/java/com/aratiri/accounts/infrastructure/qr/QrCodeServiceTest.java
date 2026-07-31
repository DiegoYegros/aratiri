package com.aratiri.accounts.infrastructure.qr;

import com.aratiri.errors.ApplicationException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class QrCodeServiceTest {

  @Test
  void getBase64_cachesRenderedQrForSameContent() {
    AtomicInteger renderCount = new AtomicInteger();
    QrCodeService service = new QrCodeService(content -> {
      renderCount.incrementAndGet();
      return "rendered:" + content;
    });

    String first = service.getBase64("lnurl1abc");
    String second = service.getBase64("lnurl1abc");

    assertEquals(first, second);
    assertEquals("rendered:lnurl1abc", first);
    assertEquals(1, renderCount.get(), "second call must be served from the cache");
  }

  @Test
  void getBase64_rendersSeparatelyPerDistinctContent() {
    AtomicInteger renderCount = new AtomicInteger();
    QrCodeService service = new QrCodeService(content -> {
      renderCount.incrementAndGet();
      return "rendered:" + content;
    });

    assertEquals("rendered:a", service.getBase64("a"));
    assertEquals("rendered:b", service.getBase64("b"));
    assertEquals(2, renderCount.get());
  }

  @Test
  void getBase64_wrapsGeneratorFailureInApplicationException() {
    QrCodeService service = new QrCodeService(content -> {
      throw new IllegalStateException("Failed to generate QR code");
    });

    ApplicationException ex = assertThrows(ApplicationException.class, () -> service.getBase64("boom"));
    assertEquals(500, ex.getStatus());
  }
}
