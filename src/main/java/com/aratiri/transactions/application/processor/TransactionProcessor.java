package com.aratiri.transactions.application.processor;

import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity;
import com.aratiri.transactions.application.dto.TransactionType;

public interface TransactionProcessor {
    long process(TransactionEntity transaction);

    TransactionType supportedType();
}