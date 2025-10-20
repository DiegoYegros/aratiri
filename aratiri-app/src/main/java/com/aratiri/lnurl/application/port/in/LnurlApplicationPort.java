package com.aratiri.lnurl.application.port.in;

import com.aratiri.lnurl.application.dto.LnurlPayRequestDTO;
import com.aratiri.lnurl.application.dto.LnurlpResponseDTO;
import com.aratiri.payments.api.dto.PaymentResponseDTO;

public interface LnurlApplicationPort {

    LnurlpResponseDTO getLnurlMetadata(String alias);

    LnurlpResponseDTO getExternalLnurlMetadata(String url);

    Object lnurlCallback(String alias, long amount, String comment);

    PaymentResponseDTO handlePayRequest(LnurlPayRequestDTO request, String userId);
}
