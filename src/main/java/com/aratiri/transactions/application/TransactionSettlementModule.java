package com.aratiri.transactions.application;

public interface TransactionSettlementModule {

    TransactionSettlementResult settleInvoiceCredit(InvoiceCreditSettlement settlement);
}
