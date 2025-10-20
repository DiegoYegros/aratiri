package com.aratiri.invoices.application;

import com.aratiri.shared.exception.AratiriException;
import com.aratiri.invoices.infrastructure.InvoiceUtils;
import com.aratiri.invoices.application.dto.DecodedInvoicetDTO;
import com.aratiri.invoices.application.dto.GenerateInvoiceDTO;
import com.aratiri.invoices.application.port.in.InvoicesPort;
import com.aratiri.invoices.application.port.out.AccountLookupPort;
import com.aratiri.invoices.application.port.out.LightningInvoicePersistencePort;
import com.aratiri.invoices.application.port.out.LightningNodePort;
import com.aratiri.invoices.domain.DecodedLightningInvoice;
import com.aratiri.invoices.domain.LightningInvoice;
import com.aratiri.invoices.domain.LightningInvoiceCreation;
import com.aratiri.invoices.domain.LightningNodeInvoice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Optional;

@Service
public class InvoicesAdapter implements InvoicesPort {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final LightningNodePort lightningNodePort;
    private final LightningInvoicePersistencePort lightningInvoicePersistencePort;
    private final AccountLookupPort accountLookupPort;

    public InvoicesAdapter(
            LightningNodePort lightningNodePort,
            LightningInvoicePersistencePort lightningInvoicePersistencePort,
            AccountLookupPort accountLookupPort
    ) {
        this.lightningNodePort = lightningNodePort;
        this.lightningInvoicePersistencePort = lightningInvoicePersistencePort;
        this.accountLookupPort = accountLookupPort;
    }

    @Override
    public GenerateInvoiceDTO generateInvoice(long satsAmount, String memo, String userId) {
        logger.info("Generating invoice for sats amount [{}] and with memo [{}] for userId [{}]", satsAmount, memo, userId);
        return createAndSaveInvoice(userId, satsAmount, memo);
    }

    @Override
    public GenerateInvoiceDTO generateInvoice(String alias, long satsAmount, String memo) {
        logger.info("Generating invoice for sats amount [{}] and with memo [{}] for alias [{}]", satsAmount, memo, alias);
        String userId = accountLookupPort.getUserIdByAlias(alias);
        return createAndSaveInvoice(userId, satsAmount, memo);
    }

    @Override
    public DecodedInvoicetDTO decodeAratiriPaymentRequest(String paymentRequest, String userId) {
        return mapToDto(decodePaymentRequestInternal(paymentRequest));
    }

    @Override
    public Optional<LightningNodeInvoice> lookupInvoice(String paymentHash) {
        return lightningNodePort.lookupInvoice(paymentHash);
    }

    @Override
    public DecodedInvoicetDTO decodePaymentRequest(String paymentRequest) {
        return mapToDto(decodePaymentRequestInternal(paymentRequest));
    }

    @Override
    public boolean existsSettledInvoiceByPaymentHash(String paymentHash) {
        return lightningInvoicePersistencePort.findSettledByPaymentHash(paymentHash).isPresent();
    }

    private GenerateInvoiceDTO createAndSaveInvoice(String userId, long satsAmount, String memo) {
        try {
            byte[] preImage = InvoiceUtils.generatePreimage();
            byte[] hash = InvoiceUtils.sha256(preImage);

            LightningInvoiceCreation creation = lightningNodePort.createInvoice(satsAmount, memo, preImage, hash);

            LightningInvoice invoice = new LightningInvoice(
                    null,
                    userId,
                    creation.paymentHash(),
                    Base64.getEncoder().encodeToString(preImage),
                    creation.paymentRequest(),
                    LightningInvoice.InvoiceState.OPEN,
                    satsAmount,
                    LocalDateTime.now(),
                    creation.expiry(),
                    0,
                    null,
                    memo
            );
            lightningInvoicePersistencePort.save(invoice);
            return new GenerateInvoiceDTO(creation.paymentRequest());
        } catch (AratiriException e) {
            throw e;
        } catch (Exception e) {
            throw new AratiriException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    private DecodedLightningInvoice decodePaymentRequestInternal(String paymentRequest) {
        String normalized = normalizePaymentRequest(paymentRequest);
        return lightningNodePort.decodePaymentRequest(normalized);
    }

    private String normalizePaymentRequest(String paymentRequest) {
        String cleanPaymentRequest = paymentRequest;
        if (cleanPaymentRequest.toLowerCase().startsWith("lightning:")) {
            cleanPaymentRequest = cleanPaymentRequest.substring(10);
        }
        return cleanPaymentRequest;
    }

    private DecodedInvoicetDTO mapToDto(DecodedLightningInvoice decoded) {
        String paymentAddressBase64 = decoded.paymentAddress() == null
                ? ""
                : Base64.getEncoder().encodeToString(decoded.paymentAddress().getBytes());
        return DecodedInvoicetDTO.builder()
                .blindedPaths(new ArrayList<>(decoded.blindedPaths()))
                .description(decoded.description())
                .descriptionHash(decoded.descriptionHash())
                .expiry(decoded.expiry())
                .destination(decoded.destination())
                .cltvExpiry(decoded.cltvExpiry())
                .numSatoshis(decoded.numSatoshis())
                .paymentAddr(paymentAddressBase64)
                .paymentHash(decoded.paymentHash())
                .timestamp(decoded.timestamp().toString())
                .fallbackAddr(decoded.fallbackAddress())
                .build();
    }
}
