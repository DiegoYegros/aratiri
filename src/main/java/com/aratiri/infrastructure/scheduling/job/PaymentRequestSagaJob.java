package com.aratiri.infrastructure.scheduling.job;

import com.aratiri.requests.application.PaymentRequestSagaService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentRequestSagaJob {

    private final PaymentRequestSagaService paymentRequestSagaService;

    @Scheduled(fixedDelayString = "${aratiri.payment-requests.saga.fixed-delay-ms:1000}")
    public void processSagaWork() {
        paymentRequestSagaService.processDueWork();
    }
}
