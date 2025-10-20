package com.aratiri.decoder.application;

import com.aratiri.config.AratiriProperties;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.shared.util.Bech32Util;
import com.aratiri.decoder.api.dto.DecodedResultDTO;
import com.aratiri.decoder.application.port.in.DecoderPort;
import com.aratiri.decoder.application.port.out.InvoiceDecodingPort;
import com.aratiri.decoder.application.port.out.LnurlPort;
import com.aratiri.decoder.application.port.out.NostrPort;
import com.aratiri.lnurl.application.dto.LnurlpResponseDTO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class DecoderAdapter implements DecoderPort {

    private static final Logger logger = LoggerFactory.getLogger(DecoderAdapter.class);

    private final InvoiceDecodingPort invoiceDecodingPort;
    private final LnurlPort lnurlPort;
    private final NostrPort nostrPort;
    private final AratiriProperties aratiriProperties;

    @Override
    public DecodedResultDTO decode(String input) {
        String workingInput = input.trim().toLowerCase();

        if (workingInput.startsWith("lnurl")) return decodeLnurl(workingInput);
        if (workingInput.startsWith("ln") || workingInput.startsWith("lightning:")) return decodeLightningInvoice(workingInput);
        if (workingInput.startsWith("npub1")) return decodeNpub(workingInput);
        if (isBitcoinAddress(workingInput)) return decodeBitcoinAddress(workingInput);

        DecodedResultDTO aliasResult = tryAliasOrLightningAddress(workingInput);
        if (aliasResult != null) return aliasResult;

        DecodedResultDTO nip05Result = tryNip05(workingInput);
        if (nip05Result != null) return nip05Result;

        logger.info("Unsupported or invalid format: {}", workingInput);
        return error("Unsupported or invalid format");
    }

    private DecodedResultDTO decodeLnurl(String input) {
        try {
            logger.info("Decoding LNURL: {}", input);
            String decodedUrl = Bech32Util.bech32Decode(input).hrp();
            LnurlpResponseDTO lnurlMetadata = decodedUrl.contains(aratiriProperties.getAratiriBaseUrl())
                    ? lnurlPort.getInternalMetadata(decodedUrl.substring(decodedUrl.lastIndexOf('/') + 1))
                    : lnurlPort.getExternalMetadata(decodedUrl);
            return success("lnurl_params", lnurlMetadata);
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    private DecodedResultDTO decodeLightningInvoice(String input) {
        try {
            logger.info("Decoding Lightning Invoice: {}", input);
            return success("lightning_invoice", invoiceDecodingPort.decodeInvoice(input));
        } catch (Exception e) {
            return error("Invalid Lightning Invoice");
        }
    }

    private DecodedResultDTO decodeNpub(String input) {
        try {
            logger.info("Resolving npub to Lightning Address: {}", input);
            String lightningAddress = nostrPort.getLud16FromNpub(input).get();
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
            return success("alias", lnurlPort.getInternalMetadata(aliasOnly));
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
                String lightningAddress = nostrPort.resolveNip05ToLud16(nip05Identifier).get(3, TimeUnit.SECONDS);
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
            return success("lnurl_params", lnurlPort.getExternalMetadata(lnurlpUrl));
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
