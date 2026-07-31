package com.aratiri.accounts.infrastructure.qr;

import com.aratiri.errors.ApplicationException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.UnaryOperator;

/**
 * Caches rendered QR PNGs: the encoded content (LNURL, bitcoin address) is immutable per account,
 * so re-rendering per read is wasted CPU. ~1-3KB per base64 PNG → ≤30MB worst case at 10k entries.
 */
@Component
public class QrCodeService {

  private static final int INTERNAL_ERROR = 500;

  private final Cache<String, String> cache;
  private final UnaryOperator<String> generator;

  @Autowired
  public QrCodeService() {
    this(QrCodeUtil::generateQrCodeBase64);
  }

  QrCodeService(UnaryOperator<String> generator) {
    this.generator = generator;
    this.cache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .build();
  }

  public String getBase64(String content) {
    return cache.get(content, this::generate);
  }

  private String generate(String content) {
    try {
      return generator.apply(content);
    } catch (IllegalStateException _) {
      throw new ApplicationException("Failed to generate QR", INTERNAL_ERROR);
    }
  }
}
