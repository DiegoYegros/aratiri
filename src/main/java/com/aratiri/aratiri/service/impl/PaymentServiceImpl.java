package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.constants.BitcoinConstants;
import com.aratiri.aratiri.dto.invoices.DecodedInvoicetDTO;
import com.aratiri.aratiri.dto.payments.PayInvoiceRequestDTO;
import com.aratiri.aratiri.dto.payments.PaymentResponseDTO;
import com.aratiri.aratiri.dto.transactions.CreateTransactionRequest;
import com.aratiri.aratiri.dto.transactions.TransactionDTOResponse;
import com.aratiri.aratiri.entity.AccountEntity;
import com.aratiri.aratiri.enums.TransactionCurrency;
import com.aratiri.aratiri.enums.TransactionStatus;
import com.aratiri.aratiri.enums.TransactionType;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.repository.AccountRepository;
import com.aratiri.aratiri.service.InvoiceService;
import com.aratiri.aratiri.service.PaymentService;
import com.aratiri.aratiri.service.TransactionsService;
import lnrpc.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import routerrpc.RouterGrpc;
import routerrpc.SendPaymentRequest;

import java.util.Iterator;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final AccountRepository accountRepository;
    private final TransactionsService transactionsService;
    private final InvoiceService invoiceService;
    private final RouterGrpc.RouterBlockingStub routerStub;

    public PaymentServiceImpl(AccountRepository accountRepository, TransactionsService transactionsService, InvoiceService invoiceService, RouterGrpc.RouterBlockingStub routerStub) {
        this.accountRepository = accountRepository;
        this.transactionsService = transactionsService;
        this.invoiceService = invoiceService;
        this.routerStub = routerStub;
    }

    @Override
    public PaymentResponseDTO payLightningInvoice(PayInvoiceRequestDTO request, String userId) {
        DecodedInvoicetDTO decodedInvoice = invoiceService.decodePaymentRequest(request.getInvoice());
        long amountSat = decodedInvoice.getNumSatoshis();
        AccountEntity account = accountRepository.findByUserId(userId);
        if (account.getBalance() < amountSat) {
            throw new AratiriException("Insufficient balance", HttpStatus.BAD_REQUEST);
        }
        CreateTransactionRequest txRequest = CreateTransactionRequest.builder()
                .userId(userId)
                .amount(BitcoinConstants.satoshisToBtc(amountSat))
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.INVOICE_DEBIT)
                .status(TransactionStatus.PENDING)
                .referenceId(decodedInvoice.getPaymentHash())
                .description("Lightning Payment: " + decodedInvoice.getDescription())
                .build();

        TransactionDTOResponse txDto = transactionsService.createTransaction(txRequest);
        initiateGrpcPayment(txDto.getId(), userId, request);
        return PaymentResponseDTO.builder()
                .transactionId(txDto.getId())
                .status(TransactionStatus.PENDING)
                .message("Payment initiated. Status is pending.")
                .build();
    }

    @Async
    public void initiateGrpcPayment(String transactionId, String userId, PayInvoiceRequestDTO payRequest) {
        try {
            SendPaymentRequest grpcRequest = SendPaymentRequest.newBuilder()
                    .setPaymentRequest(payRequest.getInvoice())
                    .setFeeLimitSat(payRequest.getFeeLimitSat() != null ? payRequest.getFeeLimitSat() : 50)
                    .setTimeoutSeconds(payRequest.getTimeoutSeconds() != null ? payRequest.getTimeoutSeconds() : 200)
                    .build();

            Iterator<Payment> paymentStream = routerStub.sendPaymentV2(grpcRequest);

            Payment finalPayment = null;
            while (paymentStream.hasNext()) {
                Payment payment = paymentStream.next();
                logger.info("GOT PAYMENT UPDATE: " + payment);

                if (payment.getStatus() == Payment.PaymentStatus.SUCCEEDED ||
                        payment.getStatus() == Payment.PaymentStatus.FAILED) {
                    finalPayment = payment;
                    break;
                }
            }

            if (finalPayment != null && finalPayment.getStatus() == Payment.PaymentStatus.SUCCEEDED) {
                transactionsService.confirmTransaction(transactionId, userId);
            } else {
                String reason = finalPayment != null ? finalPayment.getFailureReason().toString() : "Unknown failure";
                transactionsService.failTransaction(transactionId, reason);
            }

        } catch (Exception e) {
            transactionsService.failTransaction(transactionId, e.getMessage());
        }
    }
}