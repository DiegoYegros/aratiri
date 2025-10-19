package com.aratiri.lnurl.application.port.out;

import com.aratiri.dto.lnurl.LnurlCallbackResponseDTO;
import com.aratiri.dto.lnurl.LnurlpResponseDTO;

public interface LnurlRemotePort {

    LnurlpResponseDTO fetchMetadata(String url);

    LnurlCallbackResponseDTO fetchCallbackInvoice(String callbackUrl);
}
