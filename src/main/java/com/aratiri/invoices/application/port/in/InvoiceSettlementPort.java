package com.aratiri.invoices.application.port.in;

import com.aratiri.invoices.application.InternalInvoiceSettlementFacts;
import com.aratiri.invoices.application.InvoiceSettlementFacts;
import com.aratiri.invoices.application.InvoiceStateUpdate;
import com.aratiri.invoices.application.InvoiceStateUpdateResult;
import com.aratiri.invoices.application.SettleInternalInvoiceCommand;

public interface InvoiceSettlementPort {

    InvoiceSettlementFacts settlementFacts(String paymentHash);

    InternalInvoiceSettlementFacts settleInternalInvoice(SettleInternalInvoiceCommand command);

    InvoiceStateUpdateResult recordInvoiceStateUpdate(InvoiceStateUpdate update);
}
