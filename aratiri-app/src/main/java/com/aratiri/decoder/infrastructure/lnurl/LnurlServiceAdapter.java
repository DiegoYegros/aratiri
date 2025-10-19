package com.aratiri.decoder.infrastructure.lnurl;

import com.aratiri.decoder.application.port.out.LnurlPort;
import com.aratiri.dto.lnurl.LnurlpResponseDTO;
import com.aratiri.service.LnurlService;
import org.springframework.stereotype.Component;

@Component
public class LnurlServiceAdapter implements LnurlPort {

    private final LnurlService lnurlService;

    public LnurlServiceAdapter(LnurlService lnurlService) {
        this.lnurlService = lnurlService;
    }

    @Override
    public LnurlpResponseDTO getInternalMetadata(String alias) {
        return lnurlService.getLnurlMetadata(alias);
    }

    @Override
    public LnurlpResponseDTO getExternalMetadata(String url) {
        return lnurlService.getExternalLnurlMetadata(url);
    }
}
