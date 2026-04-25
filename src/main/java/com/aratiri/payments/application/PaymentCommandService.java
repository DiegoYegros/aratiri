package com.aratiri.payments.application;

import com.aratiri.infrastructure.persistence.jpa.entity.PaymentCommandEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.PaymentCommandRepository;
import com.aratiri.shared.exception.AratiriException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentCommandService {

    private final PaymentCommandRepository paymentCommandRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public PaymentCommandResult resolveIdempotency(
            String userId,
            String idempotencyKey,
            String commandType,
            String requestPayload
    ) {
        String requestHash = computeSha256(requestPayload + ":" + commandType);

        Optional<PaymentCommandEntity> existingOpt = paymentCommandRepository.findByUserIdAndIdempotencyKey(
                userId, idempotencyKey
        );

        if (existingOpt.isPresent()) {
            return handleExistingCommand(existingOpt.get(), requestHash);
        }

        // Try native insert with ON CONFLICT DO NOTHING to avoid race condition exceptions
        int inserted = entityManager.createNativeQuery("""
                INSERT INTO aratiri.payment_commands (id, user_id, idempotency_key, command_type, request_hash, status, created_at, updated_at)
                VALUES (:id, :userId, :idempotencyKey, :commandType, :requestHash, 'IN_PROGRESS', NOW(), NOW())
                ON CONFLICT (user_id, idempotency_key) DO NOTHING
                """)
                .setParameter("id", UUID.randomUUID())
                .setParameter("userId", userId)
                .setParameter("idempotencyKey", idempotencyKey)
                .setParameter("commandType", commandType)
                .setParameter("requestHash", requestHash)
                .executeUpdate();

        if (inserted == 0) {
            // Another transaction inserted the row
            PaymentCommandEntity raced = paymentCommandRepository.findByUserIdAndIdempotencyKey(
                    userId, idempotencyKey
            ).orElseThrow(() -> new AratiriException(
                    "Concurrent idempotency conflict",
                    HttpStatus.INTERNAL_SERVER_ERROR.value()
            ));
            return handleExistingCommand(raced, requestHash);
        }

        // Reload to get generated timestamps
        PaymentCommandEntity saved = paymentCommandRepository.findByUserIdAndIdempotencyKey(
                userId, idempotencyKey
        ).orElseThrow(() -> new AratiriException(
                "Failed to read inserted payment command",
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        ));

        return PaymentCommandResult.newCommand(saved.getId());
    }

    private PaymentCommandResult handleExistingCommand(PaymentCommandEntity existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new AratiriException(
                    "Idempotency key conflict: different request payload for the same key",
                    HttpStatus.CONFLICT.value()
            );
        }
        if ("ACCEPTED".equals(existing.getStatus()) || "FAILED".equals(existing.getStatus())) {
            return PaymentCommandResult.replay(
                    existing.getTransactionId(),
                    existing.getResponsePayload()
            );
        }
        return PaymentCommandResult.inProgress(existing.getTransactionId());
    }

    @Transactional
    public void completeCommand(UUID commandId, String transactionId, String responsePayload) {
        PaymentCommandEntity command = paymentCommandRepository.findById(commandId)
                .orElseThrow(() -> new AratiriException("Payment command not found"));

        command.setTransactionId(transactionId);
        command.setResponsePayload(responsePayload);
        command.setStatus("ACCEPTED");
        paymentCommandRepository.save(command);
    }

    @Transactional
    public void failCommand(UUID commandId, String responsePayload) {
        PaymentCommandEntity command = paymentCommandRepository.findById(commandId)
                .orElseThrow(() -> new AratiriException("Payment command not found"));

        command.setResponsePayload(responsePayload);
        command.setStatus("FAILED");
        paymentCommandRepository.save(command);
    }

    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new AratiriException("SHA-256 algorithm not available", HttpStatus.INTERNAL_SERVER_ERROR.value(), e);
        }
    }

    public record PaymentCommandResult(
            ResultType type,
            UUID commandId,
            String transactionId,
            String responsePayload
    ) {
        public enum ResultType {
            NEW_COMMAND,
            IN_PROGRESS,
            REPLAY
        }

        public static PaymentCommandResult newCommand(UUID commandId) {
            return new PaymentCommandResult(ResultType.NEW_COMMAND, commandId, null, null);
        }

        public static PaymentCommandResult inProgress(String transactionId) {
            return new PaymentCommandResult(ResultType.IN_PROGRESS, null, transactionId, null);
        }

        public static PaymentCommandResult replay(String transactionId, String responsePayload) {
            return new PaymentCommandResult(ResultType.REPLAY, null, transactionId, responsePayload);
        }
    }
}
