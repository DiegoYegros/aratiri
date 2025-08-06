package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.config.AratiriProperties;
import com.aratiri.aratiri.constants.BitcoinConstants;
import com.aratiri.aratiri.dto.invoices.GenerateInvoiceDTO;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.service.AccountsService;
import com.aratiri.aratiri.service.InvoiceService;
import com.aratiri.aratiri.service.LnurlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LnurlServiceImpl implements LnurlService {

    private final AccountsService accountsService;
    private final InvoiceService invoiceService;
    private final AratiriProperties properties;

    @Override
    public Object getLnurlMetadata(String alias) {
        boolean exists = accountsService.existsByAlias(alias);
        if (!exists) {
            throw new AratiriException("Alias does not match any account.", HttpStatus.NOT_FOUND);
        }
        return Map.<String, Object>of(
                "callback", properties.getAratiriBaseUrl() + "/lnurl/callback/" + alias,
                "minSendable", 1,
                "maxSendable", BitcoinConstants.SATOSHIS_PER_BTC_INTEGER,
                "metadata", "[[\"text/plain\", \"Payment to " + alias + "\"]]",
                "tag", "payRequest",
                "commentAllowed", 140
        );
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

}
