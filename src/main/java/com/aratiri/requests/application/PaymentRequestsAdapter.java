package com.aratiri.requests.application;

import com.aratiri.errors.ApplicationException;
import com.aratiri.infrastructure.configuration.AratiriProperties;
import com.aratiri.invoices.application.port.in.InvoicesPort;
import com.aratiri.invoices.domain.CreatedLightningInvoice;
import com.aratiri.invoices.domain.InvoiceCancelOutcome;
import com.aratiri.requests.application.dto.CreatePaymentRequestDTO;
import com.aratiri.requests.application.dto.OwnerPaymentRequestDTO;
import com.aratiri.requests.application.dto.PaymentRequestPageResponse;
import com.aratiri.requests.application.dto.PublicPaymentRequestDTO;
import com.aratiri.requests.application.port.in.PaymentRequestsPort;
import com.aratiri.requests.application.port.out.PaymentRequestPersistencePort;
import com.aratiri.requests.domain.PaymentRequest;
import com.aratiri.requests.domain.PaymentRequestStatus;
import com.aratiri.requests.domain.exception.PaymentRequestConflictException;
import com.aratiri.requests.domain.exception.PaymentRequestNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentRequestsAdapter implements PaymentRequestsPort {

    private static final String IDEMPOTENCY_CONFLICT_MESSAGE =
            "Idempotency key conflict: different request payload for the same key";
    private static final String PAID_CANCEL_CONFLICT_MESSAGE =
            "Payment request is already paid and cannot be cancelled";
    private static final String PAYMENT_REQUEST_NOT_FOUND_MESSAGE = "Payment request not found";

    private final PaymentRequestPersistencePort persistencePort;
    private final InvoicesPort invoicesPort;
    private final AratiriProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public PaymentRequestsAdapter(
            PaymentRequestPersistencePort persistencePort,
            InvoicesPort invoicesPort,
            AratiriProperties properties,
            Clock clock
    ) {
        this.persistencePort = persistencePort;
        this.invoicesPort = invoicesPort;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public OwnerPaymentRequestDTO create(String userId, String idempotencyKey, CreatePaymentRequestDTO request) {
        validateCreateRequest(request);
        String payloadHash = payloadHash(request);

        // Serialize concurrent first creates for the same owner+key before minting an LND invoice.
        persistencePort.lockCreateSlot(userId, idempotencyKey);

        var existing = persistencePort.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return replayOrConflict(existing.get(), payloadHash);
        }

        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(request.getExpiresInSeconds());
        String memo = normalizeMemo(request.getMemo());
        String publicId = generatePublicId();

        CreatedLightningInvoice invoice = invoicesPort.createInvoice(
                request.getAmountSats(),
                memo == null ? "" : memo,
                userId,
                null,
                null,
                request.getExpiresInSeconds()
        );

        PaymentRequest toSave = new PaymentRequest(
                UUID.randomUUID().toString(),
                publicId,
                userId,
                request.getAmountSats(),
                memo,
                PaymentRequestStatus.OPEN,
                invoice.paymentHash(),
                invoice.paymentRequest(),
                invoice.id(),
                idempotencyKey,
                payloadHash,
                now,
                expiresAt,
                null,
                null
        );

        try {
            PaymentRequest saved = persistencePort.save(toSave);
            return toOwnerDto(saved, now);
        } catch (DataIntegrityViolationException ex) {
            // Defense in depth for residual unique collisions (e.g. public_id); idempotency races
            // are prevented by lockCreateSlot before invoice minting.
            PaymentRequest raced = persistencePort.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> ex);
            return replayOrConflict(raced, payloadHash);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OwnerPaymentRequestDTO getOwned(String userId, String publicId) {
        PaymentRequest request = persistencePort.findByPublicIdAndUserId(publicId, userId)
                .orElseThrow(() -> new PaymentRequestNotFoundException(PAYMENT_REQUEST_NOT_FOUND_MESSAGE));
        return toOwnerDto(request, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentRequestPageResponse listOwned(String userId, String cursor, int limit) {
        int cappedLimit = Math.clamp(limit, 1, 200);
        List<PaymentRequest> page;
        if (cursor != null && !cursor.isEmpty()) {
            String[] parts = cursor.split("_", 2);
            Instant cursorCreatedAt = Instant.ofEpochMilli(Long.parseLong(parts[0]));
            String cursorId = parts[1];
            page = persistencePort.findByUserIdWithCursor(userId, cursorCreatedAt, cursorId, cappedLimit + 1);
        } else {
            page = persistencePort.findByUserIdFirstPage(userId, cappedLimit + 1);
        }

        boolean hasMore = page.size() > cappedLimit;
        if (hasMore) {
            page = page.subList(0, cappedLimit);
        }

        Instant now = clock.instant();
        List<OwnerPaymentRequestDTO> dtos = page.stream()
                .map(request -> toOwnerDto(request, now))
                .toList();

        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            PaymentRequest last = page.get(page.size() - 1);
            nextCursor = last.createdAt().toEpochMilli() + "_" + last.id();
        }

        return PaymentRequestPageResponse.builder()
                .paymentRequests(dtos)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    @Override
    // Intentionally not @Transactional: LND cancel can take up to ~15s. Reads/RPC/conditional
    // updates use short repository transactions so a connection is never held across the RPC.
    public OwnerPaymentRequestDTO cancel(String userId, String publicId) {
        Instant now = clock.instant();
        PaymentRequest current = persistencePort.findByPublicIdAndUserId(publicId, userId)
                .orElseThrow(() -> new PaymentRequestNotFoundException(PAYMENT_REQUEST_NOT_FOUND_MESSAGE));

        PaymentRequestStatus status = current.effectiveStatus(now);
        if (status == PaymentRequestStatus.CANCELLED) {
            // Idempotent replay: cancellation was already established; skip LND.
            return toOwnerDto(current, now);
        }
        if (status == PaymentRequestStatus.PAID) {
            throw new PaymentRequestConflictException(PAID_CANCEL_CONFLICT_MESSAGE);
        }
        if (status != PaymentRequestStatus.OPEN) {
            throw new PaymentRequestConflictException("Payment request is no longer payable and cannot be cancelled");
        }

        // Cancel on LND first so we never report CANCELLED while leaving a payable BOLT11.
        InvoiceCancelOutcome outcome = invoicesPort.cancelInvoice(current.paymentHash());
        if (outcome == InvoiceCancelOutcome.ALREADY_SETTLED) {
            // Real LND settlement is sufficient proof to heal the shareable request to PAID
            // immediately. Do not credit the ledger here; existing settlement remains exactly-once.
            persistencePort.markPaidByPaymentHash(current.paymentHash(), now);
            throw new PaymentRequestConflictException(PAID_CANCEL_CONFLICT_MESSAGE);
        }
        // CANCELLED (including already-canceled) or NOT_FOUND (absent = not payable on this node).

        int updated = persistencePort.cancelIfOpen(publicId, userId, now, now);
        PaymentRequest after = persistencePort.findByPublicIdAndUserId(publicId, userId)
                .orElseThrow(() -> new PaymentRequestNotFoundException(PAYMENT_REQUEST_NOT_FOUND_MESSAGE));

        if (updated > 0) {
            return toOwnerDto(after, now);
        }

        PaymentRequestStatus afterStatus = after.effectiveStatus(now);
        if (afterStatus == PaymentRequestStatus.CANCELLED) {
            return toOwnerDto(after, now);
        }
        if (afterStatus == PaymentRequestStatus.PAID) {
            // Settlement won the race after LND cancel returned; credit path remains authoritative.
            throw new PaymentRequestConflictException(PAID_CANCEL_CONFLICT_MESSAGE);
        }
        throw new PaymentRequestConflictException("Payment request is no longer payable and cannot be cancelled");
    }

    @Override
    @Transactional(readOnly = true)
    public PublicPaymentRequestDTO getPublic(String publicId) {
        PaymentRequest request = persistencePort.findByPublicId(publicId)
                .orElseThrow(() -> new PaymentRequestNotFoundException(PAYMENT_REQUEST_NOT_FOUND_MESSAGE));
        return toPublicDto(request, clock.instant());
    }

    private OwnerPaymentRequestDTO replayOrConflict(PaymentRequest existing, String payloadHash) {
        if (!payloadHash.equals(existing.idempotencyPayloadHash())) {
            throw new PaymentRequestConflictException(IDEMPOTENCY_CONFLICT_MESSAGE);
        }
        return toOwnerDto(existing, clock.instant());
    }

    private void validateCreateRequest(CreatePaymentRequestDTO request) {
        if (request.getAmountSats() <= 0) {
            throw new ApplicationException("amount_sats must be positive", HttpStatus.BAD_REQUEST.value());
        }
        if (request.getExpiresInSeconds() < 60 || request.getExpiresInSeconds() > 604800) {
            throw new ApplicationException(
                    "expires_in_seconds must be between 60 and 604800",
                    HttpStatus.BAD_REQUEST.value()
            );
        }
        if (request.getMemo() != null && request.getMemo().length() > 500) {
            throw new ApplicationException("memo must be at most 500 characters", HttpStatus.BAD_REQUEST.value());
        }
    }

    private String normalizeMemo(String memo) {
        if (memo == null) {
            return null;
        }
        String trimmed = memo.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String generatePublicId() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String payloadHash(CreatePaymentRequestDTO request) {
        // Must match normalizeMemo used for LND memo + stored memo so whitespace-only
        // and blank-vs-empty variants replay as the same idempotent payload.
        String memo = normalizeMemo(request.getMemo());
        String canonical = request.getAmountSats() + ":" + (memo == null ? "" : memo) + ":" + request.getExpiresInSeconds();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private OwnerPaymentRequestDTO toOwnerDto(PaymentRequest request, Instant now) {
        PaymentRequestStatus status = request.effectiveStatus(now);
        return OwnerPaymentRequestDTO.builder()
                .publicId(request.publicId())
                .shareUrl(shareUrl(request.publicId()))
                .amountSats(request.amountSats())
                .memo(request.memo())
                .status(status.name())
                .paymentRequest(status == PaymentRequestStatus.OPEN ? request.paymentRequest() : null)
                .createdAt(request.createdAt().toString())
                .expiresAt(request.expiresAt().toString())
                .paidAt(request.paidAt() == null ? null : request.paidAt().toString())
                .cancelledAt(request.cancelledAt() == null ? null : request.cancelledAt().toString())
                .build();
    }

    private PublicPaymentRequestDTO toPublicDto(PaymentRequest request, Instant now) {
        PaymentRequestStatus status = request.effectiveStatus(now);
        return PublicPaymentRequestDTO.builder()
                .publicId(request.publicId())
                .amountSats(request.amountSats())
                .memo(request.memo())
                .status(status.name())
                .paymentRequest(status == PaymentRequestStatus.OPEN ? request.paymentRequest() : null)
                .createdAt(request.createdAt().toString())
                .expiresAt(request.expiresAt().toString())
                .paidAt(request.paidAt() == null ? null : request.paidAt().toString())
                .cancelledAt(request.cancelledAt() == null ? null : request.cancelledAt().toString())
                .build();
    }

    private String shareUrl(String publicId) {
        return normalizeBaseUrl(properties.getFrontendBaseUrl()) + "/pay/" + publicId;
    }

    static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("aratiri.frontend.base.url must be configured");
        }
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}
