package com.aratiri.lnurl.application.port.out;

import com.aratiri.lnurl.application.dto.LnurlCallbackResponseDTO;
import com.aratiri.lnurl.application.dto.LnurlpResponseDTO;

public interface LnurlRemotePort {

    LnurlpResponseDTO fetchMetadata(String url);

    LnurlCallbackResponseDTO fetchCallbackInvoice(String callbackUrl);
}
