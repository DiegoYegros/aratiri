package com.aratiri.aratiri.service.processor;

import com.aratiri.aratiri.dto.transactions.TransactionType;
import com.aratiri.aratiri.entity.TransactionEntity;

import java.math.BigDecimal;

public interface TransactionProcessor {
    BigDecimal process(TransactionEntity transaction);

    TransactionType supportedType();
}
