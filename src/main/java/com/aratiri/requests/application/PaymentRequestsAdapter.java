package com.aratiri.requests.application;

import com.aratiri.errors.ApplicationException;
import com.aratiri.infrastructure.configuration.AratiriProperties;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class PaymentRequestsAdapter implements PaymentRequestsPort {

    private static final Logger logger = LoggerFactory.getLogger(PaymentRequestsAdapter.class);
    private static final String PAID_CANCEL_CONFLICT_MESSAGE =
            "Payment request is already paid and cannot be cancelled";
    private static final String PAYMENT_REQUEST_NOT_FOUND_MESSAGE = "Payment request not found";

    private final PaymentRequestPersistencePort persistencePort;
    private final PaymentRequestIntentService intentService;
    private final PaymentRequestSagaService sagaService;
    private final AratiriProperties properties;
    private final Clock clock;

    public PaymentRequestsAdapter(
            PaymentRequestPersistencePort persistencePort,
            PaymentRequestIntentService intentService,
            PaymentRequestSagaService sagaService,
            AratiriProperties properties,
            Clock clock
    ) {
        this.persistencePort = persistencePort;
        this.intentService = intentService;
        this.sagaService = sagaService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public CreatePaymentRequestResult create(String userId, String idempotencyKey, CreatePaymentRequestDTO request) {
        validateCreateRequest(request);
        String payloadHash = payloadHash(request);
        String memo = normalizeMemo(request.getMemo());

        PaymentRequestIntentService.IntentCommitResult committed =
                intentService.commitIntent(userId, idempotencyKey, request, payloadHash, memo);
        PaymentRequest saved = committed.request();

        if (saved.storedStatus() == PaymentRequestStatus.PROVISIONING) {
            try {
                sagaService.tryProvision(saved.id());
            } catch (Exception e) {
                // Worker retries from committed intent.
                logger.debug("Immediate provision deferred to saga worker for requestId={}: {}",
                        saved.id(), e.toString());
            }
            saved = persistencePort.findById(saved.id()).orElse(saved);
        }

        return new CreatePaymentRequestResult(toOwnerDto(saved, clock.instant()), committed.newlyCreated());
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
    public OwnerPaymentRequestDTO cancel(String userId, String publicId) {
        Instant now = clock.instant();
        PaymentRequest current = persistencePort.findByPublicIdAndUserId(publicId, userId)
                .orElseThrow(() -> new PaymentRequestNotFoundException(PAYMENT_REQUEST_NOT_FOUND_MESSAGE));

        Optional<OwnerPaymentRequestDTO> alreadyCancelled = cancelIfAlreadyTerminal(current, now);
        if (alreadyCancelled.isPresent()) {
            return alreadyCancelled.get();
        }
        assertCancellable(current.effectiveStatus(now));

        int updated = persistencePort.markCancelPendingIfPayable(publicId, userId, now);
        PaymentRequest after = persistencePort.findByPublicIdAndUserId(publicId, userId)
                .orElseThrow(() -> new PaymentRequestNotFoundException(PAYMENT_REQUEST_NOT_FOUND_MESSAGE));

        if (updated > 0 || after.storedStatus() == PaymentRequestStatus.CANCEL_PENDING) {
            return completeCancelPending(userId, publicId, after);
        }
        return resolveCancelRace(after);
    }

    private Optional<OwnerPaymentRequestDTO> cancelIfAlreadyTerminal(PaymentRequest current, Instant now) {
        PaymentRequestStatus status = current.effectiveStatus(now);
        if (status == PaymentRequestStatus.CANCELLED || status == PaymentRequestStatus.CANCEL_PENDING) {
            return Optional.of(toOwnerDto(current, now));
        }
        return Optional.empty();
    }

    private void assertCancellable(PaymentRequestStatus status) {
        if (status == PaymentRequestStatus.PAID) {
            throw new PaymentRequestConflictException(PAID_CANCEL_CONFLICT_MESSAGE);
        }
        if (status == PaymentRequestStatus.EXPIRED) {
            throw new PaymentRequestConflictException("Payment request is no longer payable and cannot be cancelled");
        }
        if (status == PaymentRequestStatus.FAILED) {
            throw new PaymentRequestConflictException("Payment request provisioning failed and cannot be cancelled");
        }
        if (status != PaymentRequestStatus.OPEN && status != PaymentRequestStatus.PROVISIONING) {
            throw new PaymentRequestConflictException("Payment request is no longer payable and cannot be cancelled");
        }
    }

    private OwnerPaymentRequestDTO completeCancelPending(String userId, String publicId, PaymentRequest after) {
        try {
            sagaService.tryCancel(after.id());
        } catch (Exception e) {
            // Worker retries independently.
            logger.debug("Immediate cancel deferred to saga worker for requestId={}: {}",
                    after.id(), e.toString());
        }
        PaymentRequest refreshed = persistencePort.findByPublicIdAndUserId(publicId, userId).orElse(after);
        if (refreshed.effectiveStatus(clock.instant()) == PaymentRequestStatus.PAID) {
            throw new PaymentRequestConflictException(PAID_CANCEL_CONFLICT_MESSAGE);
        }
        return toOwnerDto(refreshed, clock.instant());
    }

    private OwnerPaymentRequestDTO resolveCancelRace(PaymentRequest after) {
        PaymentRequestStatus afterStatus = after.effectiveStatus(clock.instant());
        if (afterStatus == PaymentRequestStatus.CANCELLED || afterStatus == PaymentRequestStatus.CANCEL_PENDING) {
            return toOwnerDto(after, clock.instant());
        }
        if (afterStatus == PaymentRequestStatus.PAID) {
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

    private String payloadHash(CreatePaymentRequestDTO request) {
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
