package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.config.AratiriProperties;
import com.aratiri.aratiri.dto.decoder.DecodedResultDTO;
import com.aratiri.aratiri.dto.lnurl.LnurlpResponseDTO;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.nostr.NostrService;
import com.aratiri.aratiri.service.DecoderService;
import com.aratiri.aratiri.service.InvoiceService;
import com.aratiri.aratiri.service.LnurlService;
import com.aratiri.aratiri.util.Bech32Util;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class DecoderServiceImpl implements DecoderService {

    private static final Logger logger = LoggerFactory.getLogger(DecoderServiceImpl.class);

    private final InvoiceService invoiceService;
    private final LnurlService lnurlService;
    private final NostrService nostrService;
    private final AratiriProperties aratiriProperties;

    public DecodedResultDTO decode(String input) {
        input = input.trim().toLowerCase();

        if (input.startsWith("lnurl")) return decodeLnurl(input);
        if (input.startsWith("ln") || input.startsWith("lightning:")) return decodeLightningInvoice(input);
        if (input.startsWith("npub1")) return decodeNpub(input);
        if (isBitcoinAddress(input)) return decodeBitcoinAddress(input);

        DecodedResultDTO aliasResult = tryAliasOrLightningAddress(input);
        if (aliasResult != null) return aliasResult;

        DecodedResultDTO nip05Result = tryNip05(input);
        if (nip05Result != null) return nip05Result;

        logger.info("Unsupported or invalid format: {}", input);
        return error("Unsupported or invalid format");
    }

    private DecodedResultDTO decodeLnurl(String input) {
        try {
            logger.info("Decoding LNURL: {}", input);
            String decodedUrl = Bech32Util.bech32Decode(input).hrp;
            LnurlpResponseDTO lnurlMetadata = decodedUrl.contains(aratiriProperties.getAratiriBaseUrl())
                    ? lnurlService.getLnurlMetadata(decodedUrl.substring(decodedUrl.lastIndexOf('/') + 1))
                    : lnurlService.getExternalLnurlMetadata(decodedUrl);
            return success("lnurl_params", lnurlMetadata);
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    private DecodedResultDTO decodeLightningInvoice(String input) {
        try {
            logger.info("Decoding Lightning Invoice: {}", input);
            return success("lightning_invoice", invoiceService.decodePaymentRequest(input));
        } catch (Exception e) {
            return error("Invalid Lightning Invoice");
        }
    }

    private DecodedResultDTO decodeNpub(String input) {
        try {
            logger.info("Resolving npub to Lightning Address: {}", input);
            String lightningAddress = nostrService.getLud16FromNpub(input).get();
            if (lightningAddress != null && !lightningAddress.isEmpty()) {
                return resolveLightningAddress(lightningAddress);
            }
            return error("No Lightning Address found for this npub.");
        } catch (Exception e) {
            return error("Could not resolve npub.");
        }
    }

    private boolean isBitcoinAddress(String input) {
        return input.startsWith("bitcoin:") || input.startsWith("bc1") ||
                input.startsWith("tb1") || input.startsWith("1") || input.startsWith("3");
    }

    private DecodedResultDTO decodeBitcoinAddress(String input) {
        logger.info("Decoding Bitcoin address: {}", input);
        return success("bitcoin_address", input.startsWith("bitcoin:") ? input.substring(8) : input);
    }

    private DecodedResultDTO tryAliasOrLightningAddress(String input) {
        try {
            String aliasOnly = input.contains("@") ? input.split("@")[0] : input;
            logger.info("Trying alias lookup: {}", aliasOnly);
            return success("alias", lnurlService.getLnurlMetadata(aliasOnly));
        } catch (AratiriException e) {
            if (input.contains("@")) {
                try {
                    return resolveLightningAddress(input);
                } catch (Exception ex) {
                    logger.debug("Failed to resolve '{}' as Lightning Address. Trying NIP-05...", input);
                }
            }
        }
        return null;
    }

    private DecodedResultDTO tryNip05(String input) {
        try {
            if (input.contains(".") && !input.contains(" ")) {
                String nip05Identifier = input.contains("@") ? input : "_@" + input;
                logger.info("Trying NIP-05 lookup: {}", nip05Identifier);
                String lightningAddress = nostrService.resolveNip05ToLud16(nip05Identifier).get(3, TimeUnit.SECONDS);
                if (lightningAddress != null && !lightningAddress.isEmpty()) {
                    return resolveLightningAddress(lightningAddress);
                }
            }
        } catch (Exception ex) {
            logger.debug("Failed to resolve '{}' as NIP-05: {}", input, ex.getMessage());
        }
        return null;
    }

    private DecodedResultDTO resolveLightningAddress(String lightningAddress) {
        logger.info("Resolving Lightning Address: {}", lightningAddress);
        String[] parts = lightningAddress.split("@");
        if (parts.length == 2) {
            String lnurlpUrl = "https://" + parts[1] + "/.well-known/lnurlp/" + parts[0];
            return success("lnurl_params", lnurlService.getExternalLnurlMetadata(lnurlpUrl));
        }
        throw new AratiriException("Invalid Lightning Address format.");
    }

    private DecodedResultDTO success(String type, Object data) {
        return DecodedResultDTO.builder().type(type).data(data).build();
    }

    private DecodedResultDTO error(String message) {
        return DecodedResultDTO.builder().type("error").error(message).build();
    }
}
