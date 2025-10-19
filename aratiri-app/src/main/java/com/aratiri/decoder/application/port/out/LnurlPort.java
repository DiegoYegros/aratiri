package com.aratiri.decoder.application.port.out;

import com.aratiri.dto.lnurl.LnurlpResponseDTO;

public interface LnurlPort {

    LnurlpResponseDTO getInternalMetadata(String alias);

    LnurlpResponseDTO getExternalMetadata(String url);
}
