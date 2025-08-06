package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.dto.accounts.AccountDTO;
import com.aratiri.aratiri.dto.invoices.DecodedInvoicetDTO;
import com.aratiri.aratiri.dto.invoices.GenerateInvoiceDTO;
import com.aratiri.aratiri.entity.LightningInvoiceEntity;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.repository.LightningInvoiceRepository;
import com.aratiri.aratiri.service.AccountsService;
import com.aratiri.aratiri.service.InvoiceService;
import com.aratiri.aratiri.utils.InvoiceUtils;
import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import lnrpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Optional;

@Service
public class InvoiceServiceImpl implements InvoiceService {

    private final Logger logger = LoggerFactory.getLogger(InvoiceServiceImpl.class);

    private final LightningInvoiceRepository lightningInvoiceRepository;
    private final LightningGrpc.LightningBlockingStub lightningStub;
    private final AccountsService accountsService;

    public InvoiceServiceImpl(LightningGrpc.LightningBlockingStub lightningStub, LightningInvoiceRepository lightningInvoiceRepository, AccountsService accountsService) {
        this.lightningStub = lightningStub;
        this.lightningInvoiceRepository = lightningInvoiceRepository;
        this.accountsService = accountsService;
    }

    @Override
    public GenerateInvoiceDTO generateInvoice(long satsAmount, String memo, String userId) {
        logger.info("Generating invoice for sats amount [{}] and with memo [{}] for userId [{}]", satsAmount, memo, userId);
        return createAndSaveInvoice(userId, satsAmount, memo);
    }

    @Override
    public GenerateInvoiceDTO generateInvoice(String alias, long satsAmount, String memo) {
        logger.info("Generating invoice for sats amount [{}] and with memo [{}] for alias [{}]", satsAmount, memo, alias);
        AccountDTO accountByAlias = accountsService.getAccountByAlias(alias);
        return createAndSaveInvoice(accountByAlias.getUserId(), satsAmount, memo);
    }

    private GenerateInvoiceDTO createAndSaveInvoice(String userId, long satsAmount, String memo) {
        try {
            byte[] preImage = InvoiceUtils.generatePreimage();
            byte[] hash = InvoiceUtils.sha256(preImage);
            Invoice request = Invoice.newBuilder()
                    .setRHash(ByteString.copyFrom(hash))
                    .setMemo(memo)
                    .setRPreimage(ByteString.copyFrom(preImage))
                    .setValue(satsAmount).build();

            AddInvoiceResponse addInvoiceResponse = lightningStub.addInvoice(request);
            PayReq payReq = lightningStub.decodePayReq(PayReqString.newBuilder().setPayReq(addInvoiceResponse.getPaymentRequest()).build());
            LightningInvoiceEntity lightningInvoice = LightningInvoiceEntity.builder()
                    .userId(userId)
                    .amountSats(satsAmount)
                    .preimage(Base64.getEncoder().encodeToString(preImage))
                    .invoiceState(LightningInvoiceEntity.InvoiceState.OPEN)
                    .createdAt(LocalDateTime.now())
                    .expiry(payReq.getExpiry())
                    .paymentRequest(addInvoiceResponse.getPaymentRequest())
                    .paymentHash(payReq.getPaymentHash())
                    .amountPaidSats(0)
                    .build();

            lightningInvoiceRepository.save(lightningInvoice);
            return new GenerateInvoiceDTO(addInvoiceResponse.getPaymentRequest());
        } catch (Exception e) {
            throw new AratiriException(e.getMessage());
        }
    }

    @Override
    public DecodedInvoicetDTO decodeAratiriPaymentRequest(String paymentRequest, String userId) {
        return getDecodedInvoicetDTO(paymentRequest);
    }

    @Override
    public Optional<Invoice> lookupInvoice(String paymentHash) {
        try {
            PaymentHash request = PaymentHash.newBuilder()
                    .setRHash(ByteString.fromHex(paymentHash))
                    .build();
            Invoice invoice = lightningStub.lookupInvoice(request);
            logger.info("Got this invoice after lookup: {}", invoice);
            return Optional.of(invoice);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                logger.debug("Invoice with hash {} not found on LND node (likely external).", paymentHash);
                return Optional.empty();
            }
            throw new AratiriException("Error looking up invoice on LND node: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public DecodedInvoicetDTO decodePaymentRequest(String paymentRequest) {
        return getDecodedInvoicetDTO(paymentRequest);
    }

    @Override
    public boolean existsSettledInvoiceByPaymentHash(String paymentHash) {
        Optional<LightningInvoiceEntity> byPaymentHash = lightningInvoiceRepository
                .findByPaymentHashAndInvoiceState(paymentHash, LightningInvoiceEntity.InvoiceState.SETTLED);
        return byPaymentHash.isPresent();
    }

    private DecodedInvoicetDTO getDecodedInvoicetDTO(String paymentRequest) {
        PayReqString payReqString = PayReqString.newBuilder().setPayReq(paymentRequest).build();
        PayReq payReq = lightningStub.decodePayReq(payReqString);
        return DecodedInvoicetDTO.builder()
                .blindedPaths(new ArrayList<>())
                .description(payReq.getDescription())
                .descriptionHash(payReq.getDescriptionHash())
                .expiry(payReq.getExpiry())
                .destination(payReq.getDestination())
                .cltvExpiry(payReq.getCltvExpiry())
                .numSatoshis(payReq.getNumSatoshis())
                .paymentAddr(Base64.getEncoder().encodeToString(payReq.getPaymentAddr().toStringUtf8().getBytes()))
                .paymentHash(payReq.getPaymentHash())
                .timestamp(Instant.ofEpochMilli(payReq.getTimestamp()).toString())
                .fallbackAddr(payReq.getFallbackAddr())
                .build();
    }
}