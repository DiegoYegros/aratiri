package com.aratiri.lnurl.application;

import com.aratiri.accounts.application.port.in.AccountsPort;
import com.aratiri.infrastructure.configuration.AratiriProperties;
import com.aratiri.invoices.application.dto.GenerateInvoiceDTO;
import com.aratiri.invoices.application.port.in.InvoicesPort;
import com.aratiri.lnurl.application.command.LnurlPaymentCommandService;
import com.aratiri.lnurl.application.dto.LnurlCallbackResponseDTO;
import com.aratiri.lnurl.application.dto.LnurlPayRequestDTO;
import com.aratiri.lnurl.application.dto.LnurlpResponseDTO;
import com.aratiri.lnurl.application.port.in.LnurlApplicationPort;
import com.aratiri.lnurl.application.port.out.LnurlRemotePort;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.dto.PaymentResponseDTO;
import com.aratiri.payments.application.port.in.PaymentsPort;
import com.aratiri.bitcoin.BitcoinAmounts;
import com.aratiri.errors.ApplicationException;
import com.aratiri.infrastructure.http.destination.OutboundDestinationRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final LnurlPaymentCommandService lnurlPaymentCommand;

    public LnurlAdapter(
            AccountsPort accountsPort,
            InvoicesPort invoicesPort,
            PaymentsPort paymentsPort,
            AratiriProperties properties,
            LnurlRemotePort lnurlRemotePort,
            LnurlPaymentCommandService lnurlPaymentCommand
    ) {
        this.accountsPort = accountsPort;
        this.invoicesPort = invoicesPort;
        this.paymentsPort = paymentsPort;
        this.properties = properties;
        this.lnurlRemotePort = lnurlRemotePort;
        this.lnurlPaymentCommand = lnurlPaymentCommand;
    }

    @Override
    public LnurlpResponseDTO getLnurlMetadata(String alias) {
        boolean exists = accountsPort.existsByAlias(alias);
        if (!exists) {
            throw new ApplicationException("Alias does not match any account.", HttpStatus.NOT_FOUND.value());
        }
        LnurlpResponseDTO response = new LnurlpResponseDTO();
        response.setCallback(properties.getAratiriBaseUrl() + "/lnurl/callback/" + alias);
        response.setMinSendable(1000L);
        response.setMaxSendable(BitcoinAmounts.SATOSHIS_PER_BTC_LONG * 1000);
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
        } catch (OutboundDestinationRejectedException e) {
            throw new ApplicationException(
                    OutboundDestinationRejectedException.PUBLIC_MESSAGE,
                    HttpStatus.BAD_REQUEST.value(),
                    e);
        } catch (Exception _) {
            throw new ApplicationException("Failed to fetch LNURL metadata from external URL.", HttpStatus.BAD_GATEWAY.value());
        }
    }

    @Override
    public Object lnurlCallback(String alias, long amount, String comment) {
        long satoshis = amount / 1000;
        String memo = comment != null ? comment : "No description";
        // generateInvoice resolves the alias in one query and throws the same 404 as the
        // metadata endpoint when the alias is unknown.
        GenerateInvoiceDTO generateInvoiceDTO = invoicesPort.generateInvoice(alias, satoshis, memo, null, null);
        String bolt11 = generateInvoiceDTO.getPaymentRequest();
        return Map.of(
                "pr", bolt11,
                "routes", List.of()
        );
    }

    @Override
    @Transactional
    public PaymentResponseDTO handlePayRequest(LnurlPayRequestDTO request, String userId, String idempotencyKey) {
        return lnurlPaymentCommand.execute(userId, idempotencyKey, request, () -> executeLnurlPayment(request, userId));
    }

    private PaymentResponseDTO executeLnurlPayment(LnurlPayRequestDTO request, String userId) {
        LnurlCallbackResponseDTO callbackResponse = fetchCallbackInvoice(request);
        PayInvoiceRequestDTO payRequest = new PayInvoiceRequestDTO();
        payRequest.setInvoice(callbackResponse.getPaymentRequest());
        return paymentsPort.payLightningInvoiceInternal(payRequest, userId);
    }

    private LnurlCallbackResponseDTO fetchCallbackInvoice(LnurlPayRequestDTO request) {
        String finalCallbackUrl = buildCallbackUrl(request);
        LnurlCallbackResponseDTO callbackResponse;
        try {
            callbackResponse = lnurlRemotePort.fetchCallbackInvoice(finalCallbackUrl);
        } catch (OutboundDestinationRejectedException e) {
            throw new ApplicationException(
                    OutboundDestinationRejectedException.PUBLIC_MESSAGE,
                    HttpStatus.BAD_REQUEST.value(),
                    e);
        } catch (Exception _) {
            throw new ApplicationException("Failed to fetch invoice from LNURL callback.", HttpStatus.BAD_GATEWAY.value());
        }
        if (callbackResponse == null || callbackResponse.getPaymentRequest() == null || callbackResponse.getPaymentRequest().isEmpty()) {
            throw new ApplicationException("Invalid response from LNURL callback.", HttpStatus.BAD_GATEWAY.value());
        }
        return callbackResponse;
    }

    private String buildCallbackUrl(LnurlPayRequestDTO request) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(request.getCallback())
                .queryParam("amount", request.getAmountMsat());
        if (request.getComment() != null && !request.getComment().isEmpty()) {
            uriBuilder.queryParam("comment", request.getComment());
        }
        return uriBuilder.toUriString();
    }
}
