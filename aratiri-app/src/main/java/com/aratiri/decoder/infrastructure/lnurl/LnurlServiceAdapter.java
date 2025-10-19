package com.aratiri.decoder.infrastructure.lnurl;

import com.aratiri.decoder.application.port.out.LnurlPort;
import com.aratiri.dto.lnurl.LnurlpResponseDTO;
import com.aratiri.lnurl.application.port.in.LnurlApplicationPort;
import org.springframework.stereotype.Component;

@Component
public class LnurlServiceAdapter implements LnurlPort {

    private final LnurlApplicationPort lnurlApplicationPort;

    public LnurlServiceAdapter(LnurlApplicationPort lnurlApplicationPort) {
        this.lnurlApplicationPort = lnurlApplicationPort;
    }

    @Override
    public LnurlpResponseDTO getInternalMetadata(String alias) {
        return lnurlApplicationPort.getLnurlMetadata(alias);
    }

    @Override
    public LnurlpResponseDTO getExternalMetadata(String url) {
        return lnurlApplicationPort.getExternalLnurlMetadata(url);
    }
}
