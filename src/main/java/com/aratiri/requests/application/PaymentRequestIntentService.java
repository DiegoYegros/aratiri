package com.aratiri.requests.application;

import com.aratiri.invoices.infrastructure.InvoiceUtils;
import com.aratiri.requests.application.dto.CreatePaymentRequestDTO;
import com.aratiri.requests.application.port.out.PaymentRequestPersistencePort;
import com.aratiri.requests.domain.PaymentRequest;
import com.aratiri.requests.domain.PaymentRequestStatus;
import com.aratiri.requests.domain.exception.PaymentRequestConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class PaymentRequestIntentService {

    private static final String IDEMPOTENCY_CONFLICT_MESSAGE =
            "Idempotency key conflict: different request payload for the same key";

    private final PaymentRequestPersistencePort persistencePort;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public PaymentRequestIntentService(PaymentRequestPersistencePort persistencePort, Clock clock) {
        this.persistencePort = persistencePort;
        this.clock = clock;
    }

    @Transactional
    public IntentCommitResult commitIntent(
            String userId,
            String idempotencyKey,
            CreatePaymentRequestDTO request,
            String payloadHash,
            String memo
    ) {
        persistencePort.lockCreateSlot(userId, idempotencyKey);

        var existing = persistencePort.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            PaymentRequest found = existing.get();
            if (!payloadHash.equals(found.idempotencyPayloadHash())) {
                throw new PaymentRequestConflictException(IDEMPOTENCY_CONFLICT_MESSAGE);
            }
            return new IntentCommitResult(found, false);
        }

        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(request.getExpiresInSeconds());
        String publicId = generatePublicId();

        byte[] preimageBytes = InvoiceUtils.generatePreimage();
        byte[] hashBytes;
        try {
            hashBytes = InvoiceUtils.sha256(preimageBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        String preimage = Base64.getEncoder().encodeToString(preimageBytes);
        String paymentHash = HexFormat.of().formatHex(hashBytes);

        PaymentRequest toSave = new PaymentRequest(
                UUID.randomUUID().toString(),
                publicId,
                userId,
                request.getAmountSats(),
                memo,
                PaymentRequestStatus.PROVISIONING,
                paymentHash,
                preimage,
                null,
                null,
                idempotencyKey,
                payloadHash,
                now,
                expiresAt,
                null,
                null,
                0,
                now,
                null,
                null,
                null,
                0,
                null,
                null,
                null,
                null
        );

        try {
            return new IntentCommitResult(persistencePort.save(toSave), true);
        } catch (DataIntegrityViolationException ex) {
            PaymentRequest raced = persistencePort.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> ex);
            if (!payloadHash.equals(raced.idempotencyPayloadHash())) {
                throw new PaymentRequestConflictException(IDEMPOTENCY_CONFLICT_MESSAGE);
            }
            return new IntentCommitResult(raced, false);
        }
    }

    private String generatePublicId() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public record IntentCommitResult(PaymentRequest request, boolean newlyCreated) {
    }
}
