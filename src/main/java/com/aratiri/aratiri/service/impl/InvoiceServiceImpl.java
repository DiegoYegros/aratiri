package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.dto.invoices.GenerateInvoiceDTO;
import com.aratiri.aratiri.dto.invoices.PayInvoiceDTO;
import com.aratiri.aratiri.entity.LightningInvoice;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.repository.LightningInvoiceRepository;
import com.aratiri.aratiri.service.InvoiceService;
import com.aratiri.aratiri.utils.InvoiceUtils;
import com.google.protobuf.ByteString;
import lnrpc.AddInvoiceResponse;
import lnrpc.Invoice;
import lnrpc.LightningGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class InvoiceServiceImpl implements InvoiceService {

    private final Logger logger = LoggerFactory.getLogger(InvoiceServiceImpl.class);
    private final LightningInvoiceRepository lightningInvoiceRepository;

    private final LightningGrpc.LightningBlockingStub lightningStub;

    public InvoiceServiceImpl(LightningGrpc.LightningBlockingStub lightningStub, LightningInvoiceRepository lightningInvoiceRepository) {
        this.lightningStub = lightningStub;
        this.lightningInvoiceRepository = lightningInvoiceRepository;
    }

    @Override
    public GenerateInvoiceDTO generateInvoice(long satsAmount, String memo) {
        logger.info("Generating invoice for sats amount [{}] and with memo [{}]", satsAmount, memo);
        try {
            byte[] preImage = InvoiceUtils.generatePreimage();

            byte[] hash = InvoiceUtils.sha256(preImage);

            LightningInvoice lightningInvoice = new LightningInvoice();

            Invoice request = Invoice.newBuilder()
                    .setRHash(ByteString.copyFrom(hash))
                    .setMemo(memo)
                    .setRPreimage(ByteString.copyFrom(preImage))
                    .setValue(satsAmount).build();

            AddInvoiceResponse addInvoiceResponse = lightningStub.addInvoice(request);

            LightningInvoice.builder()
                    .userId(UUID.randomUUID().toString())
                    .amountSats(satsAmount)
                    .invoiceState(LightningInvoice.InvoiceState.OPEN)
                    .createdAt(LocalDateTime.now())
                    .expiry(1) // ?
                    .paymentRequest(addInvoiceResponse.getPaymentRequest())
                    .paymentHash(addInvoiceResponse.getRHash().toStringUtf8())
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
}