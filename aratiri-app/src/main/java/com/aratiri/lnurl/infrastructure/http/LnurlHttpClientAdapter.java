package com.aratiri.lnurl.infrastructure.http;

import com.aratiri.dto.lnurl.LnurlCallbackResponseDTO;
import com.aratiri.dto.lnurl.LnurlpResponseDTO;
import com.aratiri.lnurl.application.port.out.LnurlRemotePort;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class LnurlHttpClientAdapter implements LnurlRemotePort {

    private final RestTemplate restTemplate;

    public LnurlHttpClientAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public LnurlpResponseDTO fetchMetadata(String url) {
        return restTemplate.getForObject(url, LnurlpResponseDTO.class);
    }

    @Override
    public LnurlCallbackResponseDTO fetchCallbackInvoice(String callbackUrl) {
        return restTemplate.getForObject(callbackUrl, LnurlCallbackResponseDTO.class);
    }
}
