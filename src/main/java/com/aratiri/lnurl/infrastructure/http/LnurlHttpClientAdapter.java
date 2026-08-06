package com.aratiri.lnurl.infrastructure.http;

import com.aratiri.infrastructure.configuration.WebConfig;
import com.aratiri.infrastructure.http.destination.OutboundDestinationPolicy;
import com.aratiri.lnurl.application.dto.LnurlCallbackResponseDTO;
import com.aratiri.lnurl.application.dto.LnurlpResponseDTO;
import com.aratiri.lnurl.application.port.out.LnurlRemotePort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class LnurlHttpClientAdapter implements LnurlRemotePort {

    private final RestTemplate restTemplate;
    private final OutboundDestinationPolicy outboundDestinationPolicy;

    public LnurlHttpClientAdapter(
            @Qualifier(WebConfig.OUTBOUND_USER_HTTP) RestTemplate restTemplate,
            OutboundDestinationPolicy outboundDestinationPolicy) {
        this.restTemplate = restTemplate;
        this.outboundDestinationPolicy = outboundDestinationPolicy;
    }

    @Override
    public LnurlpResponseDTO fetchMetadata(String url) {
        outboundDestinationPolicy.validate(url);
        return restTemplate.getForObject(url, LnurlpResponseDTO.class);
    }

    @Override
    public LnurlCallbackResponseDTO fetchCallbackInvoice(String callbackUrl) {
        outboundDestinationPolicy.validate(callbackUrl);
        return restTemplate.getForObject(callbackUrl, LnurlCallbackResponseDTO.class);
    }
}
