package com.aratiri.aratiri.service;

import com.aratiri.aratiri.dto.payments.PayInvoiceRequestDTO;
import com.aratiri.aratiri.dto.payments.PaymentResponseDTO;

public interface PaymentService {
    PaymentResponseDTO payLightningInvoice(PayInvoiceRequestDTO request, String userId);
}
