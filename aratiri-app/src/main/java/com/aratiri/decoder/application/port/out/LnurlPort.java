package com.aratiri.decoder.application.port.out;

import com.aratiri.lnurl.application.dto.LnurlpResponseDTO;

public interface LnurlPort {

    LnurlpResponseDTO getInternalMetadata(String alias);

    LnurlpResponseDTO getExternalMetadata(String url);
}
