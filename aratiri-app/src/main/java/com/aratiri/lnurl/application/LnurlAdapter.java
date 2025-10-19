package com.aratiri.lnurl.application;

import com.aratiri.accounts.application.port.in.AccountsPort;
import com.aratiri.config.AratiriProperties;
import com.aratiri.core.constants.BitcoinConstants;
import com.aratiri.core.exception.AratiriException;
import com.aratiri.dto.invoices.GenerateInvoiceDTO;
import com.aratiri.dto.lnurl.LnurlCallbackResponseDTO;
import com.aratiri.dto.lnurl.LnurlPayRequestDTO;
import com.aratiri.dto.lnurl.LnurlpResponseDTO;
import com.aratiri.invoices.application.port.in.InvoicesPort;
import com.aratiri.lnurl.application.port.in.LnurlApplicationPort;
import com.aratiri.lnurl.application.port.out.LnurlRemotePort;
import com.aratiri.payments.api.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.api.dto.PaymentResponseDTO;
import com.aratiri.payments.application.port.in.PaymentsPort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Service
public class LnurlAdapter implements LnurlApplicationPort {

    private final AccountsPort accountsPort;
    private final InvoicesPort invoicesPort;
    private final PaymentsPort paymentsPort;
    private final AratiriProperties properties;
    private final LnurlRemotePort lnurlRemotePort;

    public LnurlAdapter(
            AccountsPort accountsPort,
            InvoicesPort invoicesPort,
            PaymentsPort paymentsPort,
            AratiriProperties properties,
            LnurlRemotePort lnurlRemotePort
    ) {
        this.accountsPort = accountsPort;
        this.invoicesPort = invoicesPort;
        this.paymentsPort = paymentsPort;
        this.properties = properties;
        this.lnurlRemotePort = lnurlRemotePort;
    }

    @Override
    public LnurlpResponseDTO getLnurlMetadata(String alias) {
        boolean exists = accountsPort.existsByAlias(alias);
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
            return lnurlRemotePort.fetchMetadata(url);
        } catch (Exception e) {
            throw new AratiriException("Failed to fetch LNURL metadata from external URL.", HttpStatus.BAD_GATEWAY);
        }
    }

    @Override
    public Object lnurlCallback(String alias, long amount, String comment) {
        boolean exists = accountsPort.existsByAlias(alias);
        if (!exists) {
            throw new AratiriException("Alias does not match any account.", HttpStatus.NOT_FOUND);
        }
        long satoshis = amount / 1000;
        String memo = comment != null ? comment : "No description";
        GenerateInvoiceDTO generateInvoiceDTO = invoicesPort.generateInvoice(alias, satoshis, memo);
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
            callbackResponse = lnurlRemotePort.fetchCallbackInvoice(finalCallbackUrl);
        } catch (Exception e) {
            throw new AratiriException("Failed to fetch invoice from LNURL callback.", HttpStatus.BAD_GATEWAY);
        }
        if (callbackResponse == null || callbackResponse.getPaymentRequest() == null || callbackResponse.getPaymentRequest().isEmpty()) {
            throw new AratiriException("Invalid response from LNURL callback.", HttpStatus.BAD_GATEWAY);
        }
        PayInvoiceRequestDTO payRequest = new PayInvoiceRequestDTO();
        payRequest.setInvoice(callbackResponse.getPaymentRequest());
        return paymentsPort.payLightningInvoice(payRequest, userId);
    }
}
