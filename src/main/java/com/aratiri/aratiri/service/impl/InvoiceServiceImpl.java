package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.dto.accounts.AccountDTO;
import com.aratiri.aratiri.dto.invoices.DecodedInvoicetDTO;
import com.aratiri.aratiri.dto.invoices.GenerateInvoiceDTO;
import com.aratiri.aratiri.dto.invoices.PayInvoiceDTO;
import com.aratiri.aratiri.entity.LightningInvoiceEntity;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.repository.LightningInvoiceRepository;
import com.aratiri.aratiri.service.AccountsService;
import com.aratiri.aratiri.service.InvoiceService;
import com.aratiri.aratiri.utils.InvoiceUtils;
import com.google.protobuf.ByteString;
import lnrpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Objects;

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
        logger.info("Generating invoice for sats amount [{}] and with memo [{}]", satsAmount, memo);
        try {
            byte[] preImage = InvoiceUtils.generatePreimage();
            byte[] hash = InvoiceUtils.sha256(preImage);
            Invoice request = Invoice.newBuilder()
                     .setRHash(ByteString.copyFrom(hash))
                    .setMemo(memo)
                    .setRPreimage(ByteString.copyFrom(preImage))
                    .setValue(satsAmount).build();

            AddInvoiceResponse addInvoiceResponse = lightningStub.addInvoice(request);

            long expiry = lightningStub.decodePayReq(PayReqString.newBuilder().setPayReq(addInvoiceResponse.getPaymentRequest()).build()).getExpiry();
            LightningInvoiceEntity lightningInvoice = LightningInvoiceEntity.builder()
                    .userId(userId)
                    .amountSats(satsAmount)
                    .preimage(Base64.getEncoder().encodeToString(preImage))
                    .invoiceState(LightningInvoiceEntity.InvoiceState.OPEN)
                    .createdAt(LocalDateTime.now())
                    .expiry(expiry)
                    .paymentRequest(addInvoiceResponse.getPaymentRequest())
                    .paymentHash(addInvoiceResponse.getRHash().toStringUtf8())
                    .amountPaidSats(0)
                    .build();

            lightningInvoiceRepository.save(lightningInvoice);
            return new GenerateInvoiceDTO(addInvoiceResponse.getPaymentRequest());
        } catch (Exception e) {
            throw new AratiriException(e.getMessage());
        }

    }

    @Override
    public GenerateInvoiceDTO generateInvoice(String alias, long satsAmount, String memo) {
        logger.info("Generating invoice for sats amount [{}] and with memo [{}]", satsAmount, memo);
        try {
            byte[] preImage = InvoiceUtils.generatePreimage();
            logger.debug("generated preIamge: [{}]", preImage);
            byte[] hash = InvoiceUtils.sha256(preImage);
            logger.debug("Generated Hash: [{}]", hash);
            Invoice request = Invoice.newBuilder()
                    .setRHash(ByteString.copyFrom(hash))
                    .setMemo(memo)
                    .setRPreimage(ByteString.copyFrom(preImage))
                    .setValue(satsAmount).build();

            AddInvoiceResponse addInvoiceResponse = lightningStub.addInvoice(request);
            AccountDTO accountByAlias = accountsService.getAccountByAlias(alias);
            long expiry = lightningStub.decodePayReq(PayReqString.newBuilder().setPayReq(addInvoiceResponse.getPaymentRequest()).build()).getExpiry();
            LightningInvoiceEntity lightningInvoice = LightningInvoiceEntity.builder()
                    .userId(accountByAlias.getUserId())
                    .amountSats(satsAmount)
                    .preimage(Base64.getEncoder().encodeToString(preImage))
                    .invoiceState(LightningInvoiceEntity.InvoiceState.OPEN)
                    .createdAt(LocalDateTime.now())
                    .expiry(expiry)
                    .paymentRequest(addInvoiceResponse.getPaymentRequest())
                    .paymentHash(addInvoiceResponse.getRHash().toStringUtf8())
                    .amountPaidSats(0)
                    .build();

            lightningInvoiceRepository.save(lightningInvoice);
            return new GenerateInvoiceDTO(addInvoiceResponse.getPaymentRequest());
        } catch (Exception e) {
            throw new AratiriException(e.getMessage());
        }
    }

    @Override
    public PayInvoiceDTO payInvoice(String paymentRquest) {
        return null;
    }

    @Override
    public DecodedInvoicetDTO decodePaymentRequest(String paymentRequest, String userId) {
        LightningInvoiceEntity invoice = lightningInvoiceRepository
                .findByPaymentRequest(paymentRequest)
                .orElseThrow(() -> new AratiriException("Invoice not found", HttpStatus.BAD_REQUEST));

        if (!Objects.equals(invoice.getUserId(), userId)) {
            throw new AratiriException("Lightning Invoice was not generated by the current user.", HttpStatus.BAD_REQUEST);
        }

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