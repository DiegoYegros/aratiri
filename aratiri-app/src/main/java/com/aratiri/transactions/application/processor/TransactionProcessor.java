package com.aratiri.transactions.application.processor;

import com.aratiri.dto.transactions.TransactionType;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity;

import java.math.BigDecimal;

public interface TransactionProcessor {
    BigDecimal process(TransactionEntity transaction);

    TransactionType supportedType();
}
