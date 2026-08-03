package com.aratiri.requests.infrastructure;

import com.aratiri.infrastructure.persistence.jpa.entity.PaymentRequestEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.PaymentRequestRepository;
import com.aratiri.requests.application.port.out.PaymentRequestPersistencePort;
import com.aratiri.requests.domain.PaymentRequest;
import com.aratiri.requests.domain.PaymentRequestStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class PaymentRequestRepositoryAdapter implements PaymentRequestPersistencePort {

    private final PaymentRequestRepository paymentRequestRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public PaymentRequestRepositoryAdapter(PaymentRequestRepository paymentRequestRepository) {
        this.paymentRequestRepository = paymentRequestRepository;
    }

    @Override
    public void lockCreateSlot(String userId, String idempotencyKey) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:lockKey))")
                .setParameter("lockKey", "payment_request:" + userId + ":" + idempotencyKey)
                .getSingleResult();
    }

    @Override
    public PaymentRequest save(PaymentRequest request) {
        PaymentRequestEntity saved = paymentRequestRepository.save(toEntity(request));
        return toDomain(saved);
    }

    @Override
    public Optional<PaymentRequest> findById(String id) {
        return paymentRequestRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<PaymentRequest> findByIdForUpdate(String id) {
        return paymentRequestRepository.findByIdForUpdate(id).map(this::toDomain);
    }

    @Override
    public Optional<PaymentRequest> findByPublicId(String publicId) {
        return paymentRequestRepository.findByPublicId(publicId).map(this::toDomain);
    }

    @Override
    public Optional<PaymentRequest> findByPublicIdAndUserId(String publicId, String userId) {
        return paymentRequestRepository.findByPublicIdAndUserId(publicId, userId).map(this::toDomain);
    }

    @Override
    public Optional<PaymentRequest> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey) {
        return paymentRequestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey).map(this::toDomain);
    }

    @Override
    public Optional<PaymentRequest> findByPaymentHash(String paymentHash) {
        return paymentRequestRepository.findByPaymentHash(paymentHash).map(this::toDomain);
    }

    @Override
    public List<PaymentRequest> findByUserIdWithCursor(String userId, Instant cursorCreatedAt, String cursorId, int limit) {
        return paymentRequestRepository.findByUserIdWithCursor(
                        userId,
                        cursorCreatedAt,
                        cursorId,
                        PageRequest.of(0, limit)
                ).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<PaymentRequest> findByUserIdFirstPage(String userId, int limit) {
        return paymentRequestRepository.findByUserIdOrderByCreatedAtDescIdDesc(
                        userId,
                        PageRequest.of(0, limit)
                ).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public int markPaidByPaymentHash(String paymentHash, Instant paidAt) {
        return paymentRequestRepository.markPaidByPaymentHash(paymentHash, paidAt);
    }

    @Override
    @Transactional
    public int markCancelPendingIfPayable(String publicId, String userId, Instant now) {
        return paymentRequestRepository.markCancelPendingIfPayable(publicId, userId, now);
    }

    @Override
    public List<PaymentRequest> findDueProvisioning(Instant now, int limit) {
        return paymentRequestRepository.findDueProvisioning(now, PageRequest.of(0, limit)).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<PaymentRequest> findDueCancellations(Instant now, int limit) {
        return paymentRequestRepository.findDueCancellations(now, PageRequest.of(0, limit)).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public int claimProvisioning(String id, String lockedBy, Instant lockedUntil, Instant now) {
        return paymentRequestRepository.claimProvisioning(id, lockedBy, lockedUntil, now);
    }

    @Override
    @Transactional
    public int claimCancellation(String id, String lockedBy, Instant lockedUntil, Instant now) {
        return paymentRequestRepository.claimCancellation(id, lockedBy, lockedUntil, now);
    }

    @Override
    @Transactional
    public int finalizeProvisioningOpen(String id, String paymentRequest, String invoiceId, String lockedBy) {
        return paymentRequestRepository.finalizeProvisioningOpen(id, paymentRequest, invoiceId, lockedBy);
    }

    @Override
    @Transactional
    public int finalizeCancelled(String id, Instant cancelledAt, String lockedBy) {
        return paymentRequestRepository.finalizeCancelled(id, cancelledAt, lockedBy);
    }

    @Override
    @Transactional
    public int markProvisioningFailed(String id, String error, String lockedBy) {
        return paymentRequestRepository.markProvisioningFailed(id, error, lockedBy);
    }

    @Override
    @Transactional
    public int requeueFailedProvisioning(String publicId, Instant now) {
        return paymentRequestRepository.requeueFailedProvisioning(publicId, now);
    }

    @Override
    @Transactional
    public int scheduleProvisioningRetry(String id, String error, Instant nextAttemptAt, String lockedBy) {
        return paymentRequestRepository.scheduleProvisioningRetry(id, error, nextAttemptAt, lockedBy);
    }

    @Override
    @Transactional
    public int scheduleCancelRetry(String id, String error, Instant nextAttemptAt, String lockedBy) {
        return paymentRequestRepository.scheduleCancelRetry(id, error, nextAttemptAt, lockedBy);
    }

    @Override
    public long countDueProvisioning(Instant now) {
        return paymentRequestRepository.countDueProvisioning(now);
    }

    @Override
    public long countInProgressProvisioning(Instant now) {
        return paymentRequestRepository.countInProgressProvisioning(now);
    }

    @Override
    public long countFailedProvisioning() {
        return paymentRequestRepository.countFailedProvisioning();
    }

    @Override
    public long countDueCancellations(Instant now) {
        return paymentRequestRepository.countDueCancellations(now);
    }

    @Override
    public long countInProgressCancellations(Instant now) {
        return paymentRequestRepository.countInProgressCancellations(now);
    }

    @Override
    public long countExhaustedCancellations(int maxAttempts) {
        return paymentRequestRepository.countExhaustedCancellations(maxAttempts);
    }

    @Override
    public List<PaymentRequest> findFailed(int limit) {
        return paymentRequestRepository.findFailed(PageRequest.of(0, limit)).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<PaymentRequest> findExhaustedCancellations(int maxAttempts, int limit) {
        return paymentRequestRepository.findExhaustedCancellations(maxAttempts, PageRequest.of(0, limit))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private PaymentRequestEntity toEntity(PaymentRequest request) {
        return PaymentRequestEntity.builder()
                .id(request.id())
                .publicId(request.publicId())
                .userId(request.userId())
                .amountSats(request.amountSats())
                .memo(request.memo())
                .status(request.storedStatus().name())
                .paymentHash(request.paymentHash())
                .preimage(request.preimage())
                .paymentRequest(request.paymentRequest())
                .invoiceId(request.invoiceId())
                .idempotencyKey(request.idempotencyKey())
                .idempotencyPayloadHash(request.idempotencyPayloadHash())
                .createdAt(request.createdAt())
                .expiresAt(request.expiresAt())
                .paidAt(request.paidAt())
                .cancelledAt(request.cancelledAt())
                .provisionAttemptCount(request.provisionAttemptCount())
                .provisionNextAttemptAt(request.provisionNextAttemptAt())
                .provisionLockedUntil(request.provisionLockedUntil())
                .provisionLockedBy(request.provisionLockedBy())
                .provisionLastError(request.provisionLastError())
                .cancelAttemptCount(request.cancelAttemptCount())
                .cancelNextAttemptAt(request.cancelNextAttemptAt())
                .cancelLockedUntil(request.cancelLockedUntil())
                .cancelLockedBy(request.cancelLockedBy())
                .cancelLastError(request.cancelLastError())
                .build();
    }

    private PaymentRequest toDomain(PaymentRequestEntity entity) {
        return new PaymentRequest(
                entity.getId(),
                entity.getPublicId(),
                entity.getUserId(),
                entity.getAmountSats(),
                entity.getMemo(),
                PaymentRequestStatus.valueOf(entity.getStatus()),
                entity.getPaymentHash(),
                entity.getPreimage(),
                entity.getPaymentRequest(),
                entity.getInvoiceId(),
                entity.getIdempotencyKey(),
                entity.getIdempotencyPayloadHash(),
                entity.getCreatedAt(),
                entity.getExpiresAt(),
                entity.getPaidAt(),
                entity.getCancelledAt(),
                entity.getProvisionAttemptCount(),
                entity.getProvisionNextAttemptAt(),
                entity.getProvisionLockedUntil(),
                entity.getProvisionLockedBy(),
                entity.getProvisionLastError(),
                entity.getCancelAttemptCount(),
                entity.getCancelNextAttemptAt(),
                entity.getCancelLockedUntil(),
                entity.getCancelLockedBy(),
                entity.getCancelLastError()
        );
    }
}
