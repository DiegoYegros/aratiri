package com.aratiri.service;

import com.aratiri.dto.lnurl.LnurlPayRequestDTO;
import com.aratiri.dto.lnurl.LnurlpResponseDTO;
import com.aratiri.dto.payments.PaymentResponseDTO;

public interface LnurlService {
    LnurlpResponseDTO getLnurlMetadata(String alias);

    LnurlpResponseDTO getExternalLnurlMetadata(String url);

    Object lnurlCallback(String alias, long amount, String comment);

    PaymentResponseDTO handlePayRequest(LnurlPayRequestDTO request, String userId);

}