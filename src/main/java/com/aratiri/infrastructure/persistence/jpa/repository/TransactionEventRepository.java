package com.aratiri.infrastructure.persistence.jpa.repository;

import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TransactionEventRepository extends JpaRepository<TransactionEventEntity, String> {

    List<TransactionEventEntity> findByTransaction_IdOrderByCreatedAtAsc(String transactionId);

    List<TransactionEventEntity> findByTransaction_IdInOrderByCreatedAtAsc(Collection<String> transactionIds);
}
