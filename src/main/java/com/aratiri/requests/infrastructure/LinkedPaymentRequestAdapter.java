package com.aratiri.requests.infrastructure;

import com.aratiri.infrastructure.persistence.jpa.repository.PaymentRequestRepository;
import com.aratiri.invoices.application.port.out.LinkedPaymentRequestPort;
import com.aratiri.invoices.application.port.out.OwnedPaymentRequestInvoiceSeed;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

@Component
public class LinkedPaymentRequestAdapter implements LinkedPaymentRequestPort {

    private static final Set<String> OWNED_RECOVERABLE_STATUSES = Set.of(
            "PROVISIONING",
            "OPEN",
            "CANCEL_PENDING",
            "CANCELLED",
            "PAID",
            "FAILED"
    );

    private final PaymentRequestRepository paymentRequestRepository;

    public LinkedPaymentRequestAdapter(PaymentRequestRepository paymentRequestRepository) {
        this.paymentRequestRepository = paymentRequestRepository;
    }

    @Override
    @Transactional
    public void markPaidByPaymentHash(String paymentHash, Instant paidAt) {
        paymentRequestRepository.markPaidByPaymentHash(paymentHash, paidAt);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OwnedPaymentRequestInvoiceSeed> findOwnedInvoiceSeedByPaymentHash(String paymentHash) {
        return paymentRequestRepository.findByPaymentHash(paymentHash)
                .filter(entity -> OWNED_RECOVERABLE_STATUSES.contains(entity.getStatus()))
                .filter(entity -> entity.getPreimage() != null && !entity.getPreimage().isBlank())
                .map(entity -> {
                    long expirySeconds = Math.max(
                            1L,
                            Duration.between(entity.getCreatedAt(), entity.getExpiresAt()).getSeconds()
                    );
                    return new OwnedPaymentRequestInvoiceSeed(
                            entity.getUserId(),
                            entity.getPaymentHash(),
                            entity.getPreimage(),
                            entity.getPaymentRequest(),
                            entity.getAmountSats(),
                            entity.getMemo(),
                            expirySeconds
                    );
                });
    }
}
