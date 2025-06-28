package com.aratiri.aratiri.repository;

import com.aratiri.aratiri.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionsRepository extends JpaRepository<TransactionEntity, String> {
}
