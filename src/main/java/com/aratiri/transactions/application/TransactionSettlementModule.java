package com.aratiri.transactions.application;

public interface TransactionSettlementModule {

    TransactionSettlementResult settleInvoiceCredit(InvoiceCreditSettlement settlement);

    TransactionSettlementResult settleOnChainCredit(OnChainCreditSettlement settlement);

    TransactionSettlementResult settleExternalDebit(ExternalDebitCompletionSettlement settlement);

    TransactionSettlementResult failExternalDebit(ExternalDebitFailureSettlement settlement);

    void applyLightningRoutingFee(LightningRoutingFeeSettlement settlement);

    void settleInternalTransfer(InternalTransferSettlement settlement);
}
