package com.aratiri.aratiri.service;

import com.aratiri.aratiri.dto.lnurl.LnurlPayRequestDTO;
import com.aratiri.aratiri.dto.lnurl.LnurlpResponseDTO;
import com.aratiri.aratiri.dto.payments.PaymentResponseDTO;

public interface LnurlService {
    LnurlpResponseDTO getLnurlMetadata(String alias);

    LnurlpResponseDTO getExternalLnurlMetadata(String url);

    Object lnurlCallback(String alias, long amount, String comment);

    PaymentResponseDTO handlePayRequest(LnurlPayRequestDTO request, String userId);

}