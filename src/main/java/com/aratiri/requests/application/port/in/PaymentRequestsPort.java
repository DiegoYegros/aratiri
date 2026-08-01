package com.aratiri.requests.application.port.in;

import com.aratiri.requests.application.dto.CreatePaymentRequestDTO;
import com.aratiri.requests.application.dto.OwnerPaymentRequestDTO;
import com.aratiri.requests.application.dto.PaymentRequestPageResponse;
import com.aratiri.requests.application.dto.PublicPaymentRequestDTO;

public interface PaymentRequestsPort {

    OwnerPaymentRequestDTO create(String userId, String idempotencyKey, CreatePaymentRequestDTO request);

    OwnerPaymentRequestDTO getOwned(String userId, String publicId);

    PaymentRequestPageResponse listOwned(String userId, String cursor, int limit);

    OwnerPaymentRequestDTO cancel(String userId, String publicId);

    PublicPaymentRequestDTO getPublic(String publicId);
}
