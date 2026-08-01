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
        // Namespaced single-key advisory xact lock; released automatically at transaction end.
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
    public int cancelIfOpen(String publicId, String userId, Instant cancelledAt, Instant now) {
        return paymentRequestRepository.cancelIfOpen(publicId, userId, cancelledAt, now);
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
                .paymentRequest(request.paymentRequest())
                .invoiceId(request.invoiceId())
                .idempotencyKey(request.idempotencyKey())
                .idempotencyPayloadHash(request.idempotencyPayloadHash())
                .createdAt(request.createdAt())
                .expiresAt(request.expiresAt())
                .paidAt(request.paidAt())
                .cancelledAt(request.cancelledAt())
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
                entity.getPaymentRequest(),
                entity.getInvoiceId(),
                entity.getIdempotencyKey(),
                entity.getIdempotencyPayloadHash(),
                entity.getCreatedAt(),
                entity.getExpiresAt(),
                entity.getPaidAt(),
                entity.getCancelledAt()
        );
    }
}
