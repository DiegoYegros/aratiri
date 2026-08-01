package com.aratiri.invoices.application;

import com.aratiri.invoices.application.dto.DecodedInvoicetDTO;
import com.aratiri.invoices.application.dto.GenerateInvoiceDTO;
import com.aratiri.invoices.application.port.in.InvoicesPort;
import com.aratiri.invoices.application.port.out.AccountLookupPort;
import com.aratiri.invoices.application.port.out.LightningInvoicePersistencePort;
import com.aratiri.invoices.application.port.out.LightningNodePort;
import com.aratiri.invoices.domain.CreatedLightningInvoice;
import com.aratiri.invoices.domain.DecodedLightningInvoice;
import com.aratiri.invoices.domain.InvoiceCancelOutcome;
import com.aratiri.invoices.domain.LightningInvoice;
import com.aratiri.invoices.domain.LightningInvoiceCreation;
import com.aratiri.invoices.domain.LightningNodeInvoice;
import com.aratiri.invoices.infrastructure.InvoiceUtils;
import com.aratiri.errors.ApplicationException;
import com.aratiri.webhooks.application.InvoiceCreatedWebhookFacts;
import com.aratiri.webhooks.application.WebhookEventService;
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
    private final WebhookEventService webhookEventService;

    public InvoicesAdapter(
            LightningNodePort lightningNodePort,
            LightningInvoicePersistencePort lightningInvoicePersistencePort,
            AccountLookupPort accountLookupPort,
            WebhookEventService webhookEventService
    ) {
        this.lightningNodePort = lightningNodePort;
        this.lightningInvoicePersistencePort = lightningInvoicePersistencePort;
        this.accountLookupPort = accountLookupPort;
        this.webhookEventService = webhookEventService;
    }

    @Override
    public GenerateInvoiceDTO generateInvoice(long satsAmount, String memo, String userId, String externalReference, String metadata) {
        logger.info("Generating invoice for sats amount [{}] and with memo [{}] for userId [{}]", satsAmount, memo, userId);
        return new GenerateInvoiceDTO(createAndSaveInvoice(userId, satsAmount, memo, externalReference, metadata, null).paymentRequest());
    }

    @Override
    public GenerateInvoiceDTO generateInvoice(String alias, long satsAmount, String memo, String externalReference, String metadata) {
        logger.info("Generating invoice for sats amount [{}] and with memo [{}] for alias [{}]", satsAmount, memo, alias);
        String userId = accountLookupPort.getUserIdByAlias(alias);
        return new GenerateInvoiceDTO(createAndSaveInvoice(userId, satsAmount, memo, externalReference, metadata, null).paymentRequest());
    }

    @Override
    public CreatedLightningInvoice createInvoice(
            long satsAmount,
            String memo,
            String userId,
            String externalReference,
            String metadata,
            long expirySeconds
    ) {
        logger.info(
                "Generating invoice for sats amount [{}] memo [{}] userId [{}] expirySeconds [{}]",
                satsAmount,
                memo,
                userId,
                expirySeconds
        );
        return createAndSaveInvoice(userId, satsAmount, memo, externalReference, metadata, expirySeconds);
    }

    @Override
    public InvoiceCancelOutcome cancelInvoice(String paymentHash) {
        logger.info("Cancelling Lightning invoice for paymentHash [{}]", paymentHash);
        return lightningNodePort.cancelInvoice(paymentHash);
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

    private CreatedLightningInvoice createAndSaveInvoice(
            String userId,
            long satsAmount,
            String memo,
            String externalReference,
            String metadata,
            Long expirySecondsOverride
    ) {
        try {
            byte[] preImage = InvoiceUtils.generatePreimage();
            byte[] hash = InvoiceUtils.sha256(preImage);

            LightningInvoiceCreation creation = expirySecondsOverride == null
                    ? lightningNodePort.createInvoice(satsAmount, memo, preImage, hash)
                    : lightningNodePort.createInvoice(satsAmount, memo, preImage, hash, expirySecondsOverride);

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
                    memo,
                    externalReference,
                    metadata
            );
            LightningInvoice savedInvoice = lightningInvoicePersistencePort.save(invoice);
            webhookEventService.createInvoiceCreatedEvent(InvoiceCreatedWebhookFacts.from(savedInvoice));
            return new CreatedLightningInvoice(
                    savedInvoice.id(),
                    savedInvoice.paymentHash(),
                    savedInvoice.paymentRequest(),
                    savedInvoice.expiry()
            );
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new ApplicationException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
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
