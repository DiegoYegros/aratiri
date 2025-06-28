package com.aratiri.aratiri.service.processor;

import com.aratiri.aratiri.entity.TransactionEntity;
import com.aratiri.aratiri.entity.TransactionType;

import java.math.BigDecimal;

public interface TransactionProcessor {
    BigDecimal process(TransactionEntity transaction);
    TransactionType supportedType();
}
