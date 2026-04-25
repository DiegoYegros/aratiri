package com.aratiri.infrastructure.persistence.jpa.repository;

import com.aratiri.infrastructure.persistence.jpa.entity.PaymentCommandEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentCommandRepository extends JpaRepository<PaymentCommandEntity, UUID> {

    Optional<PaymentCommandEntity> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);
}
