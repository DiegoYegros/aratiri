package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.config.AratiriProperties;
import com.aratiri.aratiri.constants.BitcoinConstants;
import com.aratiri.aratiri.dto.invoices.GenerateInvoiceDTO;
import com.aratiri.aratiri.dto.lnurl.LnurlCallbackResponseDTO;
import com.aratiri.aratiri.dto.lnurl.LnurlPayRequestDTO;
import com.aratiri.aratiri.dto.lnurl.LnurlpResponseDTO;
import com.aratiri.aratiri.dto.payments.PayInvoiceRequestDTO;
import com.aratiri.aratiri.dto.payments.PaymentResponseDTO;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.service.AccountsService;
import com.aratiri.aratiri.service.InvoiceService;
import com.aratiri.aratiri.service.LnurlService;
import com.aratiri.aratiri.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LnurlServiceImpl implements LnurlService {

    private final AccountsService accountsService;
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final AratiriProperties properties;
    private final RestTemplate restTemplate;

    @Override
    public LnurlpResponseDTO getLnurlMetadata(String alias) {
        boolean exists = accountsService.existsByAlias(alias);
        if (!exists) {
            throw new AratiriException("Alias does not match any account.", HttpStatus.NOT_FOUND);
        }
        LnurlpResponseDTO response = new LnurlpResponseDTO();
        response.setCallback(properties.getAratiriBaseUrl() + "/lnurl/callback/" + alias);
        response.setMinSendable(1000L);
        response.setMaxSendable(BitcoinConstants.SATOSHIS_PER_BTC_LONG * 1000);
        response.setMetadata("[[\"text/plain\", \"Payment to " + alias + "\"]]");
        response.setTag("payRequest");
        response.setCommentAllowed(140);
        response.setStatus("OK");
        return response;
    }

    @Override
    public LnurlpResponseDTO getExternalLnurlMetadata(String url) {
        try {
            return restTemplate.getForObject(url, LnurlpResponseDTO.class);
        } catch (Exception e) {
            throw new AratiriException("Failed to fetch LNURL metadata from external URL.", HttpStatus.BAD_GATEWAY);
        }
    }

    @Override
    public Object lnurlCallback(String alias, long amount, String comment) {
        boolean exists = accountsService.existsByAlias(alias);
        if (!exists) {
            throw new AratiriException("Alias does not match any account.", HttpStatus.NOT_FOUND);
        }
        long satoshis = amount / 1000;
        String memo = "LNURL-pay to " + alias + (comment != null ? ": " + comment : "");
        GenerateInvoiceDTO generateInvoiceDTO = invoiceService.generateInvoice(alias, satoshis, memo);
        String bolt11 = generateInvoiceDTO.getPaymentRequest();
        return Map.of(
                "pr", bolt11,
                "routes", List.of()
        );
    }

    @Override
    public PaymentResponseDTO handlePayRequest(LnurlPayRequestDTO request, String userId) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(request.getCallback())
                .queryParam("amount", request.getAmountMsat());
        if (request.getComment() != null && !request.getComment().isEmpty()) {
            uriBuilder.queryParam("comment", request.getComment());
        }
        String finalCallbackUrl = uriBuilder.toUriString();
        LnurlCallbackResponseDTO callbackResponse;
        try {
            callbackResponse = restTemplate.getForObject(finalCallbackUrl, LnurlCallbackResponseDTO.class);
        } catch (Exception e) {
            throw new AratiriException("Failed to fetch invoice from LNURL callback.", HttpStatus.BAD_GATEWAY);
        }
        if (callbackResponse == null || callbackResponse.getPaymentRequest() == null || callbackResponse.getPaymentRequest().isEmpty()) {
            throw new AratiriException("Invalid response from LNURL callback.", HttpStatus.BAD_GATEWAY);
        }
        PayInvoiceRequestDTO payRequest = new PayInvoiceRequestDTO();
        payRequest.setInvoice(callbackResponse.getPaymentRequest());
        return paymentService.payLightningInvoice(payRequest, userId);
    }
}