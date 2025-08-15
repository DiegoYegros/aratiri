package com.aratiri.aratiri.service;

import com.aratiri.aratiri.config.AratiriProperties;
import com.aratiri.aratiri.dto.decoder.DecodedResultDTO;
import com.aratiri.aratiri.dto.lnurl.LnurlpResponseDTO;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.nostr.NostrService;
import com.aratiri.aratiri.util.Bech32Util;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class DecoderService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final InvoiceService invoiceService;
    private final LnurlService lnurlService;
    private final NostrService nostrService;
    private final AratiriProperties aratiriProperties;

    public DecodedResultDTO decode(String input) {
        input = input.trim().toLowerCase();
        if (input.startsWith("lnurl")) {
            try {
                String decodedUrl = Bech32Util.bech32Decode(input).hrp;
                LnurlpResponseDTO lnurlMetadata;
                if (decodedUrl.contains(aratiriProperties.getAratiriBaseUrl())) {
                    lnurlMetadata = lnurlService.getLnurlMetadata(decodedUrl.substring(decodedUrl.lastIndexOf('/') + 1));
                } else {
                    lnurlMetadata = lnurlService.getExternalLnurlMetadata(decodedUrl);
                }
                return DecodedResultDTO.builder()
                        .type("lnurl_params")
                        .data(lnurlMetadata)
                        .build();
            } catch (Exception e) {
                return DecodedResultDTO.builder().type("error").error(e.getMessage()).build();
            }
        }
        if (input.startsWith("ln")) {
            try {
                return DecodedResultDTO.builder()
                        .type("lightning_invoice")
                        .data(invoiceService.decodePaymentRequest(input))
                        .build();
            } catch (Exception e) {
                return DecodedResultDTO.builder().type("error").error("Invalid Lightning Invoice").build();
            }
        }
        if (input.startsWith("npub1")) {
            try {
                String lightningAddress = nostrService.getLud16FromNpub(input).get();
                if (lightningAddress != null && !lightningAddress.isEmpty()) {
                    return resolveLightningAddress(lightningAddress);
                }
                return DecodedResultDTO.builder().type("error").error("No Lightning Address found for this npub.").build();
            } catch (Exception e) {
                return DecodedResultDTO.builder().type("error").error("Could not resolve npub.").build();
            }
        }
        if (input.startsWith("bitcoin:") || input.startsWith("bc1") || input.startsWith("tb1") || input.startsWith("1") || input.startsWith("3")) {
            return DecodedResultDTO.builder()
                    .type("bitcoin_address")
                    .data(input.startsWith("bitcoin:") ? input.substring(8) : input)
                    .build();
        }
        try {
            String aliasOnly = input.contains("@") ? input.split("@")[0] : input;
            return DecodedResultDTO.builder()
                    .type("alias")
                    .data(lnurlService.getLnurlMetadata(aliasOnly))
                    .build();
        } catch (AratiriException e) {
            if (input.contains("@")) {
                try {
                    return resolveLightningAddress(input);
                } catch (Exception ex) {
                    logger.debug("Failed to resolve '{}' as a standard Lightning Address. Will try NIP-05.", input);
                }
            }
        }
        try {
            if (input.contains(".") && !input.contains(" ")) {
                String nip05Identifier = input;
                if (!nip05Identifier.contains("@")) {
                    nip05Identifier = "_@" + nip05Identifier;
                }
                String lightningAddress = nostrService.resolveNip05ToLud16(nip05Identifier).get(3, TimeUnit.SECONDS);
                if (lightningAddress != null && !lightningAddress.isEmpty()) {
                    return resolveLightningAddress(lightningAddress);
                }
            }
        } catch (Exception ex) {
            logger.debug("Failed to resolve '{}' as a NIP-05 identifier: {}", input, ex.getMessage());
        }

        return DecodedResultDTO.builder().type("error").error("Unsupported or invalid format").build();
    }

    private DecodedResultDTO resolveLightningAddress(String lightningAddress) {
        String[] parts = lightningAddress.split("@");
        if (parts.length == 2) {
            String username = parts[0];
            String domain = parts[1];
            String lnurlpUrl = "https://" + domain + "/.well-known/lnurlp/" + username;
            return DecodedResultDTO.builder()
                    .type("lnurl_params")
                    .data(lnurlService.getExternalLnurlMetadata(lnurlpUrl))
                    .build();
        }
        throw new AratiriException("Invalid Lightning Address format.");
    }
}